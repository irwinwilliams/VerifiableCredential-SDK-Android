// Copyright (c) Microsoft Corporation. All rights reserved

package com.microsoft.portableIdentity.sdk.auth.deprecated.oidc

import com.microsoft.portableIdentity.sdk.cards.deprecated.ClaimObject
import com.microsoft.portableIdentity.sdk.cards.deprecated.ClaimResponse
import com.microsoft.portableIdentity.sdk.crypto.CryptoOperations
import com.microsoft.portableIdentity.sdk.crypto.models.Sha
import com.microsoft.portableIdentity.sdk.crypto.models.webCryptoApi.JsonWebKey
import com.microsoft.portableIdentity.sdk.crypto.protocols.jose.DidKeyResolver
import com.microsoft.portableIdentity.sdk.crypto.protocols.jose.jws.JwsFormat
import com.microsoft.portableIdentity.sdk.crypto.protocols.jose.jws.JwsToken
import com.microsoft.portableIdentity.sdk.identifier.Identifier
import com.microsoft.portableIdentity.sdk.resolvers.IResolver
import com.microsoft.portableIdentity.sdk.utilities.*
import com.microsoft.portableIdentity.sdk.utilities.Serializer
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.content.ByteArrayContent
import io.ktor.http.ContentType
import kotlinx.serialization.*
import java.util.*
import kotlin.math.floor

class OidcResponse (
    val responder: Identifier,
    val crypto: CryptoOperations,
    val nonce: String,
    val state: String? = null,
    val claims: MutableList<ClaimObject> = mutableListOf(),
    private val redirectUrl: String,
    private val responseMode: String
    ) {
    @Serializable
    data class OidcResponseObject(
        @Required
        val iss: String = SELFISSUED,
        val sub: String, // thumbprint (sha-256)
        val aud: String,
        val nonce: String,
        val did: String?,
        @SerialName("sub_jwk")
        val subJwk: JsonWebKey,
        val iat: Int,
        val exp: Int,

        // NON COMPLIANT STATE
        val state: String? = null,
        @SerialName("_claim_names")
        val claimNames: Map<String, String>? = null,
        @SerialName("_claim_sources")
        val claimSources: Map<String, List<Map<String, String>>>? = null
    )

    companion object {
        const val SELFISSUED = "https://self-issued.me"

        fun create(oidcRequest: OidcRequest, respondWithIdentifier: Identifier): OidcResponse {
            return OidcResponse(
                responder = respondWithIdentifier,
                crypto = oidcRequest.crypto,
                nonce = oidcRequest.nonce,
                state = oidcRequest.state,
                redirectUrl = oidcRequest.redirectUrl,
                responseMode = oidcRequest.responseMode
            )
        }

        suspend fun parseAndVerify(data: String,
                                   clockSkewInMinutes: Int = 5,
                                   issuedWithinLastMinutes: Int? = null,
                                   crypto: CryptoOperations,
                                   resolver: IResolver,
                                   contentType: ContentType): OidcResponse {
            return when(contentType) {
                ContentType.Application.FormUrlEncoded -> {
                    val idToken = getQueryStringParameter(
                        OAuthRequestParameter.IdToken,
                        data
                    ) ?: throw SdkLog.error("No id_token given.")
                    val state = getQueryStringParameter(
                        OAuthRequestParameter.State,
                        data
                    )
                    val token = JwsToken(idToken)
                    val response = Serializer.parse(OidcResponseObject.serializer(), token.content())

                    val clockSkew = clockSkewInMinutes * 60
                    val currentTime = Date().time / 1000
                    if (currentTime - clockSkew < response.exp) {
                        throw SdkLog.error("Id token has expired.")
                    }
                    if (issuedWithinLastMinutes != null &&
                        (response.iat < (currentTime - clockSkew - (issuedWithinLastMinutes * 60)))) {
                        throw SdkLog.error("Id token issued before time frame set by issuedWithinLastMinutes ($issuedWithinLastMinutes)")
                    }

                    val responder = if (response.did != null) {
                        resolver.resolve(response.did, crypto)
                    } else {
                        DidKeyResolver.resolveIdentiferFromKid(token.signatures.first {
                            !it.getKid().isNullOrBlank()
                        }.getKid()!!, crypto, resolver)
                    }

                    DidKeyResolver.verifyJws(token, crypto, responder)
                    val claimObjects = mutableListOf<ClaimObject>()
                    if (response.claimNames != null) {
                        // for each claim class
                        response.claimNames.forEach {
                            claimClass ->
                            val claims = response.claimSources?.get(claimClass.value) ?: throw SdkLog.error("Could not find claims for ${claimClass.key}")
                            claims.forEach { claim ->
                                if (claim.containsKey("JWT")) {
                                    val claimObjectData = JwsToken(claim["JWT"]!!)
                                    DidKeyResolver.verifyJws(claimObjectData, crypto, responder)
                                    val claimObject = Serializer.parse(ClaimObject.serializer(), claimObjectData.content())
                                    if (claimObject.claimClass != claimClass.key) {
                                        throw SdkLog.error("Claim Object class does not match expected class.")
                                    }
                                    claimObject.verify(crypto, resolver)
                                    claimObjects.add(claimObject)
                                }
                            }
                        }
                    }

                    OidcResponse(
                        responder,
                        crypto,
                        response.nonce,
                        state,
                        claimObjects,
                        response.aud,
                        "form_post"
                    )
                }
                else -> {
                    throw SdkLog.error("Unable to parse content of type $contentType")
                }
            }
        }
    }

    fun addClaim(claim: ClaimObject) {
        this.claims.add(claim)
    }

    /**
     * @param expiresIn Minutes until the OIDC response requires
     */
    suspend fun signAndSend(
        expiresIn: Int = 5,
        useKey: String = responder.signatureKeyReference
    ): ClaimObject? {
        val currentTime = Date().time
        val expiration = currentTime + 1000 * 60 * expiresIn
        val exp = floor(expiration / 1000f).toInt()
        val iat = floor( currentTime / 1000f).toInt()
        val key = crypto.keyStore.getPublicKey(useKey).getKey()

        var claimNames: MutableMap<String, String>? = null
        var claimSources: MutableMap<String, MutableList<Map<String, String>>>? = null
        if (!claims.isNullOrEmpty()) {
            claimNames = mutableMapOf()
            claimSources = mutableMapOf()
            claims.forEachIndexed { index, it ->
                val claimData = Serializer.stringify(ClaimObject.serializer(), it)
                val token = JwsToken(claimData)
                token.sign(useKey, crypto)
                val serialized = token.serialize(JwsFormat.Compact)
                val name = if (claimNames.containsKey(it.claimClass)) {
                    claimNames[it.claimClass]!!
                } else {
                    claimNames[it.claimClass] = "src$index"
                    "src$index"
                }
                if (!claimSources.containsKey(name)) {
                    claimSources[name] = mutableListOf()
                }
                claimSources[name]?.add(mapOf(
                    "JWT" to serialized
                ))
            }
        }

        val response = OidcResponseObject(
            sub = key.getThumbprint(crypto, Sha.Sha256),
            aud = redirectUrl,
            nonce = nonce,
            did = responder.document.id,
            subJwk = key.toJWK(),
            iat = iat,
            exp = exp,
            state = state,
            claimNames = claimNames,
            claimSources = claimSources
        )
        val responseData = Serializer.stringify(OidcResponseObject.serializer(), response)
        println("Responding with data: $responseData")
        val token = JwsToken(responseData)
        token.sign(useKey, crypto)
        val responseSerialized = token.serialize(JwsFormat.Compact)

        return send(responseSerialized)?.claimObject
    }

    private suspend fun send(idToken: String): ClaimResponse? {
        return when (responseMode) {
            OidcRequest.defaultResponseMode -> {
//                val responseBody = "id_token=${idToken}" + if (!state.isNullOrBlank()) {
//                    "&state=${state}"
                    // DISABLED WHILE EnterpiseAgent is not percent decoding
                val responseBody = "id_token=${PercentEncoding.encode(idToken)}" + if (!state.isNullOrBlank()) {
                    "&state=${PercentEncoding.encode(state)}"
                } else {
                    ""
                }
                println("Encoded as: $responseBody")
                val response = try {
                    getHttpClient().post<String> {
                        url(redirectUrl)
                        body = ByteArrayContent(
                            bytes = stringToByteArray(responseBody),
                            contentType = ContentType.Application.FormUrlEncoded
                        )
                    }
                } catch (exception: Exception) {
                    println("Exception sending response: ${exception.message}")
                    throw exception
                }
                if (response.isNotBlank()) {
                    try {
                        Serializer.parse(ClaimResponse.serializer(), response)
                    } catch (error: SerializationException) {
                        // this was not the right format but we did not get a 400 error
                        null
                    }
                } else {
                    null
                }
            }
            else -> {
                throw SdkLog.error("Unknown Response Mode $responseMode")
            }
        }
    }
}
/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/

package com.microsoft.portableIdentity.sdk.auth.models.oidc

import com.microsoft.portableIdentity.sdk.utilities.Constants.CLAIM_NAMES
import com.microsoft.portableIdentity.sdk.utilities.Constants.CLAIM_SOURCES
import com.microsoft.portableIdentity.sdk.utilities.Constants.SELF_ISSUED
import com.microsoft.portableIdentity.sdk.utilities.Constants.SUB_JWK
import com.microsoft.portableIdentity.sdk.crypto.models.webCryptoApi.JsonWebKey
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contents of an OpenID Self-Issued Token Response.
 *
 * @see [OpenID Spec](https://openid.net/specs/openid-connect-core-1_0.html#JWTRequests)
 */
@Serializable
data class OidcResponseContent(
    // iss property always needs to be set to https://self-issued.me
    @Required
    val iss: String = SELF_ISSUED,

    // thumbprint (sha-256) of the public key
    val sub: String,

    // url that is meant to receive the response.
    val aud: String,

    // nonce from the request.
    val nonce: String? = null,

    // state from the request.
    val state: String? = null,

    // did tied to the private key that signed response.
    val did: String?,

    // the public key that can be used to verify signature.
    @SerialName(SUB_JWK)
    val subJwk: JsonWebKey,

    // time the token was issued.
    val iat: Long,

    // time the token expires.
    val exp: Long,

    // PICS specific
    // response contains claims that fulfills this contract.
    val contract: String? = null,

    //id of the response
    val jti: String? = null,

    // attestations that were asked for in request.
    val attestations: AttestationResponse? = null,

    // vc needed for Revocation or Exchange PIC API
    val vc: String? = null,
    // recipient of VC for Exchange PIC API
    val recipient: String? = null
)
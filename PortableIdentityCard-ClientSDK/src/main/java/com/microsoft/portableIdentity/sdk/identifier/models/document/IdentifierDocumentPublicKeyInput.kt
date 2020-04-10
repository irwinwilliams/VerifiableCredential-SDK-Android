// Copyright (c) Microsoft Corporation. All rights reserved

package com.microsoft.portableIdentity.sdk.identifier.models.document

import com.microsoft.portableIdentity.sdk.crypto.keys.PublicKey
import com.microsoft.portableIdentity.sdk.crypto.keys.ellipticCurve.EllipticCurvePublicKey
import com.microsoft.portableIdentity.sdk.crypto.keys.rsa.RsaPublicKey
import com.microsoft.portableIdentity.sdk.crypto.models.webCryptoApi.JsonWebKey
import com.microsoft.portableIdentity.sdk.identifier.deprecated.document.LinkedDataKeySpecification
import com.microsoft.portableIdentity.sdk.utilities.SdkLog
import kotlinx.serialization.Serializable

@Serializable
data class IdentifierDocumentPublicKeyInput (
    /**
     * The id of the public key in the format
     * {keyIdentifier}
     */
    val id: String,

    /**
     * The type of the public key.
     */
    val type: String,

    /**
     * The owner of the key.
     */
    val controller: String? = null,

    @Deprecated("against spec", ReplaceWith("this.controller"))
    val owner: String? = null,

    /**
     * The JWK public key.
     */
    val jwk: JsonWebKey
) {
    fun toPublicKey(): PublicKey {
        return when (type) {
            in LinkedDataKeySpecification.RsaSignature2018.values -> {
                return RsaPublicKey(this.jwk)
            }
            in LinkedDataKeySpecification.EcdsaSecp256k1Signature2019.values -> {
                return EllipticCurvePublicKey(this.jwk)
            }
            in LinkedDataKeySpecification.EcdsaKoblitzSignature2016.values -> {
                throw SdkLog.error("${LinkedDataKeySpecification.EcdsaKoblitzSignature2016.name} not supported.")
            }
            in LinkedDataKeySpecification.Ed25519Signature2018.values -> {
                throw SdkLog.error("${LinkedDataKeySpecification.Ed25519Signature2018.name} not supported.")
            }
            else -> {
                throw SdkLog.error("Unknown key type: $type")
            }
        }
    }
}
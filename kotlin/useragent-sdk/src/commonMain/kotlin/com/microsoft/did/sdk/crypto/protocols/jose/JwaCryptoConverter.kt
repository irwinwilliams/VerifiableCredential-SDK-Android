package com.microsoft.did.sdk.crypto.protocols.jose

import com.microsoft.did.sdk.crypto.models.webCryptoApi.Algorithm
import com.microsoft.did.sdk.crypto.models.webCryptoApi.EcdsaParams
import com.microsoft.did.sdk.crypto.models.webCryptoApi.RsaOaepParams
import com.microsoft.did.sdk.crypto.models.webCryptoApi.W3cCryptoApiConstants

class JwaCryptoConverter {
    companion object {
        fun jwaAlgToWebCrypto(algorithm: String): Algorithm {
            return when (algorithm.toUpperCase()) {
                JoseConstants.Rs256.value, JoseConstants.Rs384.value, JoseConstants.Rs512.value -> {
                    // get hash size
                    val hashSize = Regex("[Rr][Ss](\\d+)").matchEntire(algorithm)!!.groupValues[0]
                    Algorithm(
                        name = W3cCryptoApiConstants.RsaSsaPkcs1V15.value,
                        additionalParams = mapOf(
                            "hash" to Algorithm(
                                name = "SHA-$hashSize"
                            )
                        )
                    )
                }
                JoseConstants.RsaOaep.value, JoseConstants.RsaOaep256.value -> {
                    RsaOaepParams(
                        name = W3cCryptoApiConstants.RsaOaep.value,
                        additionalParams = mapOf(
                            "hash" to Algorithm(
                                name = W3cCryptoApiConstants.Sha256.value
                            )
                        )
                    )
                }
                JoseConstants.Es256K.value -> {
                    EcdsaParams(
                        name = W3cCryptoApiConstants.EcDsa.value,
                        hash = Algorithm(
                            name = W3cCryptoApiConstants.Sha256.value
                        ),
                        additionalParams = mapOf(
                            "namedCurve" to W3cCryptoApiConstants.Secp256k1.value,
                            "format" to "DER"
                        )
                    )
                }
                JoseConstants.EdDsa.value -> {
                    EcdsaParams(
                        name = W3cCryptoApiConstants.EdDsa.value,
                        hash = Algorithm(
                            name = W3cCryptoApiConstants.Sha256.value
                        ),
                        additionalParams = mapOf(
                            "namedCurve" to W3cCryptoApiConstants.Ed25519.value
                        )
                    )
                }
                else -> {
                    throw Error("Unknown JOSE algorithm: $algorithm")
                }
            }
        }
    }
}
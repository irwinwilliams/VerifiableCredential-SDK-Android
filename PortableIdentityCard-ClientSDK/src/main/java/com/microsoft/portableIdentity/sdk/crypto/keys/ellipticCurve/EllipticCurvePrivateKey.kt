package com.microsoft.portableIdentity.sdk.crypto.keys.ellipticCurve

import com.microsoft.portableIdentity.sdk.crypto.keys.KeyType
import com.microsoft.portableIdentity.sdk.crypto.keys.PrivateKey
import com.microsoft.portableIdentity.sdk.crypto.keys.PublicKey
import com.microsoft.portableIdentity.sdk.crypto.models.webCryptoApi.JsonWebKey

class EllipticCurvePrivateKey (key: JsonWebKey): PrivateKey(key) {
    var crv = key.crv
    var x = key.x
    var y = key.y
    override var kty = KeyType.EllipticCurve
    override var alg: String? = if (key.alg != null) key.alg!! else "ES256K"
    var d = key.d

    override fun toJWK(): JsonWebKey {
        return JsonWebKey(
            kty = kty.value,
            alg = alg,
            kid = kid,
            key_ops = key_ops?.map { use -> use.value },
            use = use?.value,
            crv = crv,
            x = x,
            y = y,
            d = d
        )
    }

    override fun getPublicKey(): PublicKey {
        return EllipticCurvePublicKey(this.toJWK())
    }
}
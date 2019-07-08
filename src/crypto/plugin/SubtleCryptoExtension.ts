/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/
import ISubtleCrypto from './ISubtleCrypto'
import IKeyStore, { CryptoAlgorithm } from '../keyStore/IKeyStore';
import CryptoFactory from './CryptoFactory';
import CryptoHelpers from '../utilities/CryptoHelpers';
import PublicKey from '../keys/PublicKey';
import PrivateKey from '../keys/PrivateKey';
import { KeyType } from '../keys/KeyTypeFactory';
import PairwiseKey from '../keys/PairwiseKey';
import { SubtleCrypto } from 'webcrypto-core';
import CryptoError from '../CryptoError';
const clone = require('clone');

// Named curves
const CURVE_P256K = 'P-256K';
const CURVE_K256 = 'K-256';

/**
 * The class extends the @class SubtleCrypto with addtional methods.
 *  Adds methods to work with key references.
 *  Extends SubtleCrypto to work with JWK keys.
 */
export default class SubtleCryptoExtension extends SubtleCrypto implements ISubtleCrypto {
  private keyStore: IKeyStore;
  private cryptoFactory: CryptoFactory;

  constructor(cryptoFactory: CryptoFactory) {
    super();
    this.keyStore = cryptoFactory.keyStore;
    this.cryptoFactory = cryptoFactory;
  }

  /**
   * Generate a pairwise key for the algorithm
   * @param algorithm for the key
   * @param seedReference Reference to the seed
   * @param personaId Id for the persona
   * @param peerId Id for the peer
   * @param extractable True if key is exportable
   * @param keyops Key operations
   */
   public async generatePairwiseKey(algorithm: EcKeyGenParams | RsaHashedKeyGenParams, seedReference: string, personaId: string, peerId: string): Promise<PrivateKey> {
    const pairwiseKey = new PairwiseKey(this.cryptoFactory);
    return pairwiseKey.generatePairwiseKey(algorithm, seedReference, personaId, peerId);
   } 

  /**
   * Sign with a key referenced in the key store
   * @param algorithm used for signature
   * @param keyReference points to key in the key store
   * @param data to sign
   * @returns The signature in the requested algorithm
   */
  public async signByKeyStore(algorithm: CryptoAlgorithm, keyReference: string, data: BufferSource): Promise<ArrayBuffer> {
    const jwk: PrivateKey = await <Promise<PrivateKey>>this.keyStore.get(keyReference, false);
    const crypto: SubtleCrypto = CryptoHelpers.getSubtleCryptoForAlgorithm(this.cryptoFactory, algorithm);
    const keyImportAlgorithm = SubtleCryptoExtension.normalizeAlgorithm(CryptoHelpers.getKeyImportAlgorithm(algorithm, jwk));
    
    const key = await crypto.importKey('jwk', SubtleCryptoExtension.normalizeJwk(jwk), keyImportAlgorithm, true, ['sign']);
    const signature = await <PromiseLike<ArrayBuffer>>crypto.sign(jwk.kty === KeyType.EC ? <EcdsaParams>SubtleCryptoExtension.normalizeAlgorithm(algorithm): <RsaPssParams>algorithm, 
      key, 
      <ArrayBuffer>data);
    if ((<any>algorithm).format) {
      const format: string =  (<any>algorithm).format;
      if (format.toUpperCase() !== 'DER') {
        throw new CryptoError(algorithm, 'Only DER format supported for signature');
      }

      const r = signature.slice(0, signature.byteLength / 2);
      const s = signature.slice(signature.byteLength / 2, signature.byteLength)
      return SubtleCryptoExtension.toDer([r, s]);
    }

    return signature;
  }
  
  /**
   * format the signature output to DER format
   * @param elements Array of elements to encode in DER
   */
  private static toDer(elements: ArrayBuffer[]): ArrayBuffer {
    let index: number = 0;
    // calculate total size. 
    let lengthOfRemaining = 0;
    for (let element = 0 ; element < elements.length; element++) {
      // Add element format bytes
      lengthOfRemaining += 2;
      const buffer = new Uint8Array(elements[element]);
      const size = (buffer[0] & 0x80) === 0x80 ? buffer.length + 1 : buffer.length;
      lengthOfRemaining += size;
    }
    // Prepare output
    index = 0;
    const result = new Uint8Array(lengthOfRemaining + 2);
    result.set([0x30, lengthOfRemaining], index);
    index += 2;
    for (let element = 0 ; element < elements.length; element++) {
      // Add element format bytes
      const buffer = new Uint8Array(elements[element]);
      const size = (buffer[0] & 0x80) === 0x80 ? buffer.length + 1 : buffer.length;
      result.set([0x02, size], index);
      index += 2;
      if (size > buffer.length) {
        result.set([0x0], index++);
      }
      
      result.set(buffer, index);
      index += buffer.length;
    }

    return result;
  }

  /**
   * Verify with JWK.
   * @param algorithm used for verification
   * @param jwk Json web key used to verify
   * @param signature to verify
   * @param payload which was signed
   */
   public async verifyByJwk(algorithm: CryptoAlgorithm, jwk: JsonWebKey, signature: BufferSource, payload: BufferSource): Promise<boolean> {
    const crypto: SubtleCrypto = CryptoHelpers.getSubtleCryptoForAlgorithm(this.cryptoFactory, algorithm);
    const keyImportAlgorithm = SubtleCryptoExtension.normalizeAlgorithm(CryptoHelpers.getKeyImportAlgorithm(algorithm, jwk));
    
    const key = await crypto.importKey('jwk', SubtleCryptoExtension.normalizeJwk(jwk), keyImportAlgorithm, true, ['verify']);
    if ((<any>algorithm).format) {
      const format: string =  (<any>algorithm).format;
      if (format.toUpperCase() !== 'DER') {
        throw new CryptoError(algorithm, 'Only DER format supported for signature');
      }
      const elements = SubtleCryptoExtension.fromDer(<Uint8Array>signature);
      signature = new Uint8Array(elements[0].length + elements[1].length);
      (<Uint8Array>signature).set(elements[0]);
      (<Uint8Array>signature).set(elements[1], elements[1].length);      
    }
    return crypto.verify(jwk.kty === KeyType.EC ? 
      <EcdsaParams>SubtleCryptoExtension.normalizeAlgorithm(algorithm): 
      <RsaPssParams>algorithm, key, <ArrayBuffer>signature, <ArrayBuffer>payload);
   }  
  
  /**
   * format the signature output from DER format
   * @param signature to decode from DER
   */
   private static fromDer(signature: Uint8Array): Uint8Array[] {
    if (signature[0] !== 0x30) {
      throw new Error('No DER format to decode');
    }

    const lengthOfRemaining = signature[1];
    const results: Uint8Array[] = [];
    let index: number = 2;
    while (index < lengthOfRemaining) {
      const marker = signature[index++];
      if (marker !== 0x02) {
        throw new Error(`Marker on index ${index-1} must be 0x02`);
      }

      let length = signature[index++];
      while (signature[index] === 0) {
        index ++;
        length --;
      }
      const data = signature.slice(index, index + length);
      results.push(data);
      index = index + length;
    }
    return results;
  }
          
  /**
   * Decrypt with a key referenced in the key store.
   * The referenced key must be a jwk key.
   * @param algorithm used for signature
   * @param keyReference points to key in the key store
   * @param cipher to decrypt
   */
   public async decryptByKeyStore(algorithm: CryptoAlgorithm, keyReference: string, cipher: BufferSource): Promise<ArrayBuffer> {
    const jwk: PrivateKey = <PrivateKey> await this.keyStore.get(keyReference, false);
    const crypto: SubtleCrypto = CryptoHelpers.getSubtleCryptoForAlgorithm(this.cryptoFactory, algorithm);
    const keyImportAlgorithm = SubtleCryptoExtension.normalizeAlgorithm(CryptoHelpers.getKeyImportAlgorithm(algorithm, jwk));
    
    const key = await crypto.importKey('jwk', SubtleCryptoExtension.normalizeJwk(jwk), SubtleCryptoExtension.normalizeAlgorithm(keyImportAlgorithm), true, ['decrypt']);
    return crypto.decrypt(algorithm, key, <ArrayBuffer>cipher);
   }  
          
  /**
   * Decrypt with JWK.
   * @param algorithm used for decryption
   * @param jwk Json web key to decrypt
   * @param cipher to decrypt
   */
   public async decryptByJwk(algorithm: CryptoAlgorithm, jwk: JsonWebKey, cipher: BufferSource): Promise<ArrayBuffer> {
    const crypto: SubtleCrypto = CryptoHelpers.getSubtleCryptoForAlgorithm(this.cryptoFactory, algorithm);
    const keyImportAlgorithm = SubtleCryptoExtension.normalizeAlgorithm(CryptoHelpers.getKeyImportAlgorithm(algorithm, jwk));
    
    const key = await crypto.importKey('jwk', SubtleCryptoExtension.normalizeJwk(jwk), SubtleCryptoExtension.normalizeAlgorithm(keyImportAlgorithm), true, ['decrypt']);
    return crypto.decrypt(algorithm, key, <ArrayBuffer>cipher);
   }  

  /**
   * Encrypt with a jwk key referenced in the key store
   * @param algorithm used for encryption
   * @param jwk Json web key public key
   * @param data to encrypt
   */
  public async encryptByJwk(algorithm: CryptoAlgorithm, jwk: PublicKey | JsonWebKey, data: BufferSource): Promise<ArrayBuffer> {
    const keyImportAlgorithm = CryptoHelpers.getKeyImportAlgorithm(algorithm, jwk);
    const crypto: SubtleCrypto = CryptoHelpers.getSubtleCryptoForAlgorithm(this.cryptoFactory, algorithm);
    const key = await crypto.importKey('jwk', SubtleCryptoExtension.normalizeJwk(jwk), SubtleCryptoExtension.normalizeAlgorithm(keyImportAlgorithm), true, ['encrypt']);
    return <PromiseLike<ArrayBuffer>>crypto.encrypt(algorithm, key, <ArrayBuffer>data);
  }        
  
  /**
   * Normalize the algorithm so it can be used by underlying crypto.
   * @param algorithm Algorithm to be normalized
   */
  public static normalizeAlgorithm (algorithm: any) {
    if (algorithm.namedCurve) {
      if (algorithm.namedCurve === CURVE_P256K) {
        const alg = clone(algorithm);
        alg.namedCurve = CURVE_K256;
        return alg;
      }
    }

    return algorithm;
  }

  /**
   * Normalize the JWK parameters so it can be used by underlying crypto.
   * @param jwk Json web key to be normalized
   */
  public static normalizeJwk (jwk: any) {
    if (jwk.crv) {
      if (jwk.crv === CURVE_P256K) {
        const clonedKey = clone(jwk);
        clonedKey.crv = CURVE_K256;
        return clonedKey;
      }
    }

    return jwk;
  }
}

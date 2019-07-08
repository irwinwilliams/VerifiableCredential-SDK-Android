import ICommitSigner from './ICommitSigner';
import Commit from '../Commit';
import SignedCommit from '../SignedCommit';
import IKeyStore from '../../crypto/keyStore/IKeyStore';
import CryptoFactory from '../../crypto/plugin/CryptoFactory';
import CryptoOperations from '../../crypto/plugin/CryptoOperations';
import JwsToken from '../../crypto/protocols/jose/jws/JwsToken';
import { ProtectionFormat } from '../../crypto/keyStore/ProtectionFormat';
import { IJwsSigningOptions } from '../../crypto/protocols/jose/IJoseOptions';
import { TSMap } from 'typescript-map';

interface CommitSignerOptions {

  /** 
   * The DID of the identity that will the commit. 
   */
  did: string;

  /** 
   * The private key reference to be used to sign the commit. 
   */
  keyReference: string;

  /**
   * KeyStore that holds the private key to be used to sign the commit.
   */
  keyStore: IKeyStore;

  /** 
   * The CryptoSuite to be used to for the algorithm to use to sign the commit
   */
  cryptoFactory: CryptoFactory;

}

/**
 * Class which can apply a signature to a commit.
 */
export default class CommitSigner implements ICommitSigner {

  private did: string;
  private keyRef: string;
  private cryptoFactory: CryptoFactory;

  constructor(options: CommitSignerOptions) {
    this.did = options.did;
    this.keyRef = options.keyReference;
    this.cryptoFactory = options.cryptoFactory;
  }

  /**
   * Signs the given commit.
   *
   * @param commit The commit to sign.
   */
  public async sign(commit: Commit): Promise<SignedCommit> {

    let payload: string;
    if (typeof(commit.getPayload()) === 'string') {
      payload = commit.getPayload();
    } else {
      payload = JSON.stringify(commit.getPayload());
    }

    commit.validate();

    const commitFields = commit.getCommitFields();
    const finalcommitFields = new TSMap<string, string>([
                                    ['iss', this.did],
                                    ['commit_strategy', <string> commitFields.commit_strategy],
                                    ['commited_at', <string> commitFields.committed_at],
                                    ['context', <string> commitFields.context],
                                    ['interface', <string> commitFields.interface],
                                    ['operation', <string> commitFields.operation],
                                    ['sub', <string> commitFields.sub],
                                    ['type', <string> commitFields.type]
                                  ]);

    // const jws = new JwsToken(commit.getPayload(), new CryptoFactory([this.cryptoSuite]));
    // const signed = await jws.sign(key, <any> finalcommitFields); // Need to broaden TypeScript definition of JwsToken.sign().
    const signingOptions: IJwsSigningOptions = {cryptoFactory: this.cryptoFactory, protected: finalcommitFields};
    const jws = new JwsToken(signingOptions);
    const signed = await jws.sign(this.keyRef, Buffer.from(payload), ProtectionFormat.JwsCompactJson);
    const serializedCompactJws = signed.serialize();
    const [outputHeaders, outputPayload, outputSignature] = serializedCompactJws.split('.');

    return new SignedCommit({
      protected: outputHeaders,
      payload: outputPayload,
      header: undefined,
      signature: outputSignature,
    });
  }

}

// @flow
import CryptoJS from 'crypto-js';

const defaultPassword = 'redacted';

export default (input: string, password: string): string => {
  let output = input;
  try {

    // Get salt and iv from message
    const raw = new Buffer(input, 'base64');
    const salt = raw.slice(0, 16).toString('hex');
    const iv = raw.slice(16, 32).toString('hex');
    const message = raw.slice(32).toString('base64');
    if (!salt || !iv) {
      return output;
    }

    const passcode = password || defaultPassword;
    const key = CryptoJS.PBKDF2(
      passcode,
      CryptoJS.enc.Hex.parse(salt),
      {
        keySize: 128/32,
        iterations: 1000
      });
    const cipherParams = CryptoJS.lib.CipherParams.create({
      ciphertext: CryptoJS.enc.Base64.parse(message)
    });
    const decrypted = CryptoJS.AES.decrypt(
      cipherParams,
      key,
      {
        iv: CryptoJS.enc.Hex.parse(iv)
      });
    const decryptedString = decrypted.toString(CryptoJS.enc.Utf8);
    if (decryptedString) {
      // console.debug('raw:', input);
      // console.debug('decrypted:', decryptedString);
      output = decryptedString;
    }
    return output;
  } catch (err) {
    console.warn('error decrypting', err);
    return output;
  }
}
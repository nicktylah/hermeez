package com.nicktylah.hermes.util;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.nicktylah.hermes.R;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Objects;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Handles compression and decompression of byte arrays
 */
public class ByteUtils {

  private String TAG = "ByteUtils";

  byte[] compress(byte[] input, int compressionType) {
    // GZIP
//    ByteArrayOutputStream bos = new ByteArrayOutputStream(input.length);
//    GZIPOutputStream gzip;
//    byte[] compressed = new byte[0];
//    try {
//      gzip = new GZIPOutputStream(bos);
//      gzip.write(input);
//      gzip.close();
//      compressed = bos.toByteArray();
//      bos.close();
//    } catch (IOException e) {
//      e.printStackTrace();
//    }
//    return compressed;

    // DEFLATE
//    byte[] output = new byte[100];
//    Deflater compresser = new Deflater();
//    compresser.setInput(input);
//    compresser.finish();
//    int compressedDataLength = compresser.deflate(output);
//    compresser.end();

    Deflater deflater = new Deflater();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DeflaterOutputStream dos = new DeflaterOutputStream(baos, deflater);
    try {
      dos.write(input);
      baos.close();
      dos.finish();
      dos.close();
    } catch (IOException e) {
      Log.e(TAG, "Error compressing byte[]", e);
      return new byte[0];
    }
    return baos.toByteArray();
  }

  public byte[] decompress(byte[] input) {
    Inflater inflater = new Inflater(true);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ByteArrayInputStream bais = new ByteArrayInputStream(input);
    InflaterInputStream in = new InflaterInputStream(bais, inflater);
    boolean go = true;
    try {
      while (go) {
        int b = in.read();
        if (b == -1)
          go = false;
        else
          baos.write(b);
      }
      baos.close();
      in.close();
    } catch (IOException e) {
      Log.e(TAG, "Error decompressing byte[]", e);
      return new byte[0];
    }
    return baos.toByteArray();
  }


  /**
   * Looks up a user's key in private storage, returns a default if none exist
   * @param context
   * @return
   */
  public String getKey(Context context) {
    String line;
    try {
      InputStream inputStream = context.openFileInput(context.getString(R.string.hermes_pw));

      if (inputStream != null) {
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        String receiveString;
        StringBuilder stringBuilder = new StringBuilder();

        while ((receiveString = bufferedReader.readLine()) != null) {
          stringBuilder.append(receiveString);
        }

        inputStream.close();
        line = stringBuilder.toString();
        if (Objects.equals(line, "")) {
          return context.getString(R.string.default_hermes_pw);
        } else {
          return line;
        }
      }
    } catch (FileNotFoundException e) {
      Log.e(TAG, "File not found: " + e.toString());
    } catch (IOException e) {
      Log.e(TAG, "Can not read file: " + e.toString());
    }

    // This is the default key
    return context.getString(R.string.default_hermes_pw);
  }

  public String encrypt(Context context, String rawInput) {
    if (rawInput == null || Objects.equals(rawInput, "")) {
      return "";
    }
    int keySize = 128;
    int iterationCount = 1000;
//    Log.d(TAG, "[ORIGINAL]: " + rawInput);

    String KEY = getKey(context);

    // Set up secret key spec for 128-bit AES encryption and decryption
    KeyGenerator kg;
    try {
      SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
      sr.setSeed("C8%Kn#9CmpIr2VxEa^3M0x*!S5uX1IqMrpja*izg9I&Y!XT**BMGJyzVn8304uS&nv@*!KsYLyky9jOHk^3*QkYYbwdnraqR*MxT".getBytes());
      kg = KeyGenerator.getInstance("AES");
      kg.init(128, sr);
    } catch (Exception e) {
      Log.e(TAG, "AES secret key spec error", e);
      return "";
    }

    // Encode the original data with AES
    byte[] encodedBytes;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      byte[] saltBytes = kg.generateKey().getEncoded();
      byte[] ivBytes = kg.generateKey().getEncoded();

      baos.write(saltBytes);
      baos.write(ivBytes);

      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
      KeySpec spc = new PBEKeySpec(KEY.toCharArray(), saltBytes, iterationCount, keySize);
      SecretKey key = new SecretKeySpec(factory.generateSecret(spc).getEncoded(), "AES");

      cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(ivBytes));
      encodedBytes = cipher.doFinal(rawInput.getBytes("UTF-8"));
      baos.write(encodedBytes);
    } catch (Exception e) {
      Log.e(TAG, "AES encryption error", e);
      return "";
    }
    String encodedString = Base64.encodeToString(encodedBytes, Base64.DEFAULT);
//    Log.d(TAG, "[ENCODED]: " + encodedString);

//    return baos.toByteArray();
    return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
  }

  private static String bytesToHex(byte[] in) {
    final StringBuilder builder = new StringBuilder();
    for(byte b : in) {
      builder.append(String.format("%02x", b));
    }
    return builder.toString();
  }
}

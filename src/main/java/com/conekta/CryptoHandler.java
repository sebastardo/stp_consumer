package com.conekta;

import sun.misc.BASE64Encoder;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;

public class CryptoHandler {

        private static String fileName = "prueba.jks";
        private static String password = "12345678";
        private static String alias = "prueba";


        public String firmar(String empresa, Integer fechaOperacion) throws Exception {
            String firma;
            try {
                firma = sign(cadenaOriginal(empresa, fechaOperacion));

            } catch (Exception e) {
                throw new Exception("Exception" + e.getMessage(), e.getCause());
            }
            return firma;
        }

        public String cadenaOriginal(String empresa, Integer fechaOperacion) {
            StringBuilder sB = new StringBuilder();
            sB.append("|||");
            sB.append(empresa).append("|");
            sB.append(fechaOperacion == null ? "" : fechaOperacion).append("|");
            sB.append("||||||||||||||||||||||||||||||||");
            String cadena = sB.toString();

            //System.out.println("Cadena original: " + cadena);

            return cadena;
        }


        public String sign(String cadena) throws Exception {
            String retVal;
            try {
                String data = cadena;
                Signature firma = Signature.getInstance("SHA256withRSA");
                RSAPrivateKey llavePrivada = getCertified(fileName, password, alias);
                firma.initSign(llavePrivada);
                byte[] bytes = data.getBytes("UTF-8");
                firma.update(bytes, 0, bytes.length);
                BASE64Encoder b64 = new BASE64Encoder();
                retVal = b64.encode(firma.sign());

                //System.out.println("Firma: " + retVal);


            } catch (NoSuchAlgorithmException e) {
                throw new Exception("NoSuchAlgorithmException", e);
            } catch (InvalidKeyException e) {
                throw new Exception("InvalidKeyException: ", e);
            } catch (SignatureException e) {
                throw new Exception("SignatureException",e);
            } catch (NoSuchProviderException e) {
                throw new Exception("NoSuchProviderException", e);
            }
            return retVal;
        }



        private RSAPrivateKey getCertified(String keystoreFilename, String password, String alias) throws Exception {
            RSAPrivateKey privateKey;
            try {
                KeyStore keystore = KeyStore.getInstance("JKS");
                ClassLoader classloader = Thread.currentThread().getContextClassLoader();
                InputStream is = classloader.getResourceAsStream(keystoreFilename);
                keystore.load(is, password.toCharArray());
                privateKey = (RSAPrivateKey) keystore.getKey(alias, password.toCharArray());
            } catch (FileNotFoundException ex) {
                throw new Exception("FileNotFoundException", ex);
            } catch (IOException ex) {
                throw new Exception("IOException", ex);
            } catch (Exception ex) {
                throw new Exception("Exception", ex);
            }
            return privateKey;
        }
}

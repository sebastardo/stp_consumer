package com.conekta;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;

import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class Handler implements RequestHandler<Map<String,String[]>, String> {

    private static String url = "https://demo.stpmex.com:7024/speiws/rest/ordenPago/consOrdenesFech";
    private int connectionTimeout = 5000;
    private int receptionTimeout = 15000;
    private String trustStore = "cacerts";
    private String trusStorePassword = "changeit";




    @Override
    public String handleRequest(Map<String,String[]> event, Context context) {

        int statusCode;
        String mensaje = "Lambda ejecutado con exito";

        String[] empresas = event.get("empresas");
        String[] estados = event.get("estados");
        String[] fechas = event.get("fechas");

        try {
            statusCode = consultaOrdenes(empresas, estados, fechas);
        } catch (Exception e) {
            e.printStackTrace();
            statusCode = 400;
            mensaje = e.getMessage();
        }
        return "{\n'statusCode': " + statusCode + ",\n'body': json.dumps('" + mensaje +"')\n}";
    }

    private int consultaOrdenes(String[] empresas, String[] estados, String[] fecha) throws Exception {

        int status = 200;
        CryptoHandler cryptoHandler = new CryptoHandler();

        int cont = 0;
        for (String emp: empresas) {
            JSONObject jsonObject = new JSONObject();

            String empresa = emp;//"CONEKTA";
            String estado = estados[cont];
            jsonObject.put("empresa", empresa);
            jsonObject.put("estado", estado);

            if(fecha[cont]!="") {
                int fechaOperacion = 0;
                jsonObject.put("fechaOperacion", Integer.parseInt(fecha[cont]));
                jsonObject.put("firma", cryptoHandler.firmar(empresa, fechaOperacion).replace("\n", ""));
            } else {
                jsonObject.put("firma", cryptoHandler.firmar(empresa, null).replace("\n", ""));
            }

            status = HttpClient(jsonObject.toString(), url, emp);

            if(status != 200){
                return status;
            }
            cont++;
        }
        return status;
    }


    public int HttpClient(String peticion, String url, String empresa) throws Exception {

        String body;
        int respuesta = 0;

        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        try (InputStream trustStream = classloader.getResourceAsStream(this.trustStore)) {
            HttpParams httpParams = new BasicHttpParams();
            org.apache.http.params.HttpConnectionParams.setConnectionTimeout(httpParams, connectionTimeout);
            org.apache.http.params.HttpConnectionParams.setSoTimeout(httpParams, receptionTimeout);
            DefaultHttpClient httpclient = new DefaultHttpClient(httpParams);

            // System.out.println("url: " + url);


            if (url.startsWith("https")) {
                //truststore
                KeyStore trustStore1;
                trustStore1 = KeyStore.getInstance(KeyStore.getDefaultType());
                trustStore1.load(trustStream, trusStorePassword.toCharArray());
                SSLSocketFactory socketFactory = new SSLSocketFactory(trustStore1);
                Scheme sch = new Scheme("https", 443, socketFactory);
                httpclient.getConnectionManager().getSchemeRegistry().register(sch);
            }


            HttpPost httpPost = new HttpPost(url);

            System.out.println("peticion: " + peticion);

            HttpEntity entity = new ByteArrayEntity(peticion.getBytes(StandardCharsets.UTF_8));
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(entity);
            HttpResponse response = httpclient.execute(httpPost);
            respuesta = response.getStatusLine().getStatusCode();

            if (response.getStatusLine().getStatusCode() != 200) {
                System.out.println("Error con HTTPStatusCode:" + response.getStatusLine().getStatusCode());
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            StringBuilder stringBuilder = new StringBuilder();

            while ((body = br.readLine()) != null) {
                stringBuilder.append(body);
            }

            body = stringBuilder.toString();
            br.close();
            if (response.getEntity().getContent() != null) {
                response.getEntity().getContent().close();
            }


        } catch (CertificateException | UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            throw new Exception("Exception" + e.getMessage(), e.getCause());
        }


        guardarEnS3(body, empresa);

        //System.out.println(body);
        return respuesta;
    }

//    private void cargarDeS3() throws IOException {
//        AmazonS3 s3 = AmazonS3ClientBuilder.standard().build();
//        S3Object data = s3.getObject("spr-mybucket", "config.json");
//
//        S3ObjectInputStream s3Stream = data.getObjectContent();
//
//        ObjectMapper objectMapper = new ObjectMapper();
//        JsonFactory jsonFactory = objectMapper.getFactory();
//        JsonParser jsonParser = jsonFactory.createParser(s3Stream);
//
//
//    }



    private void guardarEnS3(String body, String empresa){
        /***S3***/

        LocalDateTime fecha = LocalDateTime.now();
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("yyyyMMdd");
        String fecha_formato = fecha.format(formato);

        String nombre_json = empresa + "_" + fecha_formato + ".json";

        InputStream json = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentType("application/json");

        AmazonS3 s3 = AmazonS3ClientBuilder.standard().build();
        s3.putObject("spr-mybucket", "stp/"+ nombre_json, json, meta);

    }
}

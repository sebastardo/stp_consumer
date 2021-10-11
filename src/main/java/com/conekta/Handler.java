package com.conekta;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;

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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
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

        try {
            statusCode = consultaOrdenes(empresas);
        } catch (Exception e) {
            e.printStackTrace();
            statusCode = 400;
            mensaje = e.getMessage();
        }
        return "{\n'statusCode': " + statusCode + ",\n'body': json.dumps('" + mensaje +"')\n}";
    }

    private int consultaOrdenes(String[] empresas) throws Exception {

        int status = 200;
        CryptoHandler cryptoHandler = new CryptoHandler();

        for (String emp: empresas) {

            String empresa = emp; //String empresa = "CONEKTA";
            String estado = "E";
            Integer fechaOperacion = 20210801;
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("empresa", empresa);
            jsonObject.put("estado", estado);

            //jsonObject.put("fechaOperacion", fechaOperacion);
            //jsonObject.put("firma", cryptoHandler.firmar(empresa, fechaOperacion).replace("\n", ""));

            jsonObject.put("firma", cryptoHandler.firmar(empresa, null).replace("\n", ""));
            // System.out.println(jsonObject);
            status = HttpClient(jsonObject.toString(), url, emp);

            if(status != 200){
                return status;
            }
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

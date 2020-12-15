package com.tencent.devops.plugin.service

import com.tencent.devops.common.api.util.AESUtil
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.api.util.OkhttpUtils
import com.tencent.devops.plugin.pojo.stke.StkeType
import com.tencent.devops.plugin.pojo.stke.StkeUpdateParam
import okhttp3.*
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.net.ssl.*

@Service
class StkeService @Autowired constructor() {

    companion object {
        private val logger = LoggerFactory.getLogger(StkeService::class.java)
    }

    @Value("\${stke.cert_key_pem:#{null}}")
    private val cert_key_pem: String =
        "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"

    @Value("\${stke.cert_pem:#{null}}")
    private val cert_pem: String =
        "LT0i6bL0RUrS4UoUUwDodE1wnbmAXGt62GdcKO/2c6U4MuiW2nsBuY6WfOD8JZqIYdCspJJzTzah174UPx5+aB85QoCBMers0plrd/2hN+rMIEf0ibAj+tcIPZrVXUAPe9ozRs2clulaE6UeT3g+LKurdehBcqkHrrj/q+zrD6dGlqgaaJ6y7dg/DLLhOBqjqLu+ol8I+xjk5h2uSqc2ncSVyROHeoZbs9zVq3OtvFPHqoM7Ez3RorGBgAwSReCJxEnIhsHc82sViztbRSz+NBFg9IzdxRYZstrE+KrXhmUeONQlgQiPUSY8BOaVDlLgVuV4f3B4tl7Ktc5m/G9ZB+ngANXEA4vFVchYKVpVACPRaRa+oYwhnzj8ddHDB2qCJmurnyIqQ16OkEcDKRJ3h6CtOKoTprSkLqF/QdXjRxcwNekdR8NokRtY/i0yB2GxmmKe97xIDsu4SuJW0IIbvAJvLxCY6MHq0sJ1quxXoZPqjraAq6sGxtO7frzEScihQsh1BV3i3k6YXWb+pWDh+GN/X2Yd3vXdsHXHY20O64NHcIkYH+27nzddE6vdW8hdWUZoeaKRCNCzZywP4ogUmRuCpK1Nelq7+gwlqU4dj8Dyyj4P7ZFh2fEoX2iySe/Bnsv1zg9Br6ENNo3vIxDwbdG6kNoglhF8p60VIogKf0sbWmqCILgkcDayNHmmLfJEp9qhE48KCoOqWg9GxhH+M4PBUVRZ4TRt+Ky4Y8YRvK1mvX1aKKrjJq6x2NzhBI4dY44srUl1Kb3lsaWWHI1tUr6xax8C9qoqZ+UQw3UZza8d0QyN0UiF8tLstSC7X/cLve4Uf0kPjHYU/PCUeWxhMCD05nE8R+hUvsL3/ZcPkSroJ+I9YQFVoSsUzW/fCHtQKecIfI5GC8NtKYs6PcXAWSYD2bK3VW4rEQVMTnvDzHkzxAsSRba98tgA8DYSsKzlkJ4Xji32cvd9eL9faJOB+0f+slFMaGQEBNgj00Q7PulJJQnIvb2TlW5sy68aFT4tAi9RJ+R6u6UtNouskm3zfLWoTVTqLSZCjUCNuQmlgV2MSVYc9qlUX962p16Xu90l0ENU+gcT9qww9t9YXsn4LZ2blNgoJFBXrAhDMiSSbY+Nk3ipfmu7PygEGYIqG2xEqxhoT6vG41ZugiYD8jLehMRsX70SfObZuIXuFm9hZXiJHK5TlLgV6XyeHq9ZsMz0x+wTkhLYHJT47kZfPTyKWkx35LpJZ+RSQaU9OWBqtXdTYpqMdQeB/KnTSyBYEDlyoNcOXVYc1nXlxTPEfNmjfjzxsjlnk3JqL2yOkNBIw43t3L1jwPKIXVJx5Lnaj7t+wtQCEs4qEPZjn14Rcz4weO+SOxHcVz4nKyor1+WqsQs+/CBZCDP7+phSEiSxZBuO6UhACxsYLfDlIBCgnD10Z3WQHnxN83xAiCrmwhzfTjfb0oOwO4bpXkdZ+WTlW/7vrp0gZbFlOTbPZPs4EwR/VigYjsdahDEGJyoMwQL6nY6LTX6X519Im0vXEBRmrLO9ir1oxoQZ4f+qWjC/Nn6mIPZY9MxAv5M5y+KeCImhBeoUwlHKSnj0psD0tng0rOFw+jrdYK6tsWMVG5Zzu2yFtNoU2MG1CDbl96uREFCVQjjkuk2SV8zYQ0NPQXxnf/jULDIZKQtl2iFGFekggI9TwlVL9EP6PBLoflH27pRS3QtNZA45Ps4LJ0Lo/wVBaRqXxbu/A71RFzg6AdId3TPfoI4U93rn96E6gUrLWJViP4VfpO+J6DSXfw2DFDtc4T1H3Hczf//yEgLkA7/m9Jkoq+W/KaiVYOdJlhVoHMirAg5oPDScVrVfCBMzAxL+J3KUS+bNG1CgfXW90JxOoxnAb4gTm0ZxINuESOxYhzmKgIlRwWYF3xK7id8Xtw6d9LtT9TPqvL+ic14BR3etCJy49AqTKVuyCNm/nPdj0uN4hY87oAUSkdkb9unNgf2irxWYR8LTQYPYTZkt/H+4M0WWO3IHxwmilLXf7Fwf+b8AyO9iUL/vSNP5xssmmYsjkw+lcN9lO3zuuQcEZes6TEsB3am4wK0RNyZlbYrtTslwLzprVgfyZlDuzZXT6DFrPywW744m7qUyNE2GDoJJquWUk/G0itSkfF0DmkZj5lLhNK9pf12rBGH70NgOIOF6czzog1BYwWc7n534hM2MFfFsccaVAv3Egd2PF+omRGSUAoBILi0LMA+PH76L90HMJmcsHFQcxF5uJJHOb6j2U0RVP5+ctb6Dt+Ow/nawJ34w9W3DcmJAeNDaARdRGhOQujxppWG68AlMgwl4krajDPqew6HJXhWWjUMu8NPTc+L3glfcwJqoDXvMNC2R6wg7QFyBcVeJgYevllAAhDv+wCKbK4OPbNibDYaIswsSbiv3Knk6lpzwQPRV0gFB7a2gpnYh4y0xwPCbI10+CZUXe8ExOZSO6CIrBZy0jjJS4+geRxkU4C4fp/wErOAi/Ld37VAY"

    private val key = "bk_cicert_key_pem"

//    private val client = OkHttpClient.Builder()
//        .certificatePinner(
//            CertificatePinner.Builder()
//                .add("api.kubernetes.oa.com", cert_key_pem, cert_pem)
//                .build()
//        )
//        .build()


    fun update(
        stkeType: StkeType,
        clusterName: String,
        namespace: String,
        appsName: String,
        updateParam: StkeUpdateParam
    ): Boolean {

        if (cert_key_pem == null || cert_pem == null) {
            logger.error("cert_pem/cert_key_pem can not find")
            return false
        }

//        val cert = Base64.getDecoder().decode(AESUtil.decrypt(key, cert_pem))
//        val key = Base64.getDecoder().decode(AESUtil.decrypt(key, cert_key_pem))
//        val certFile = File.createTempFile("cert", ".pem")
//        val keyFile = File.createTempFile("key", ".pem")
//
//
//        val sslContext = SSLContext.getInstance("TSL")
//        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
//        val keyStore = getStkeKeyStore()
//        trustManagerFactory.init(keyStore)
//        val trustManager = trustManagerFactory.trustManagers[0] as X509TrustManager
//        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
//        keyManagerFactory.init(keyStore, "keystore_pass".toCharArray())
//        sslContext.init(null, Array<TrustManager>(1) { trustManager }, null)
//
//        val client = OkHttpClient.Builder()
//            .sslSocketFactory(sslContext.socketFactory, trustManager)
//            .build()
//
//        return when (stkeType) {
//            StkeType.DEPLOYMENT -> {
//                val url = "https://api.kubernetes.oa.com/apis/apps/v1/namespaces/$namespace/deployments/$appsName"
//                val mediaType = MediaType.parse("application/json; charset=utf-8")
//                val requestBody = RequestBody.create(mediaType, JsonUtil.toJson(updateParam))
//                val request = Request.Builder()
//                    .url(url)
//                    .addHeader("X-TKE-ClusterName", clusterName)
//                    .put(requestBody)
//                    .build()
//
//                true
//            }
//            StkeType.STATEFUL_SET -> {
//                true
//
//            }
//            StkeType.STATEFUL_SET_PLUS -> {
//                true
//            }
        return true
    }

    fun getPodsStatus(
        clusterName: String,
        namespace: String,
        appsName: String
    ): Boolean {
        if (cert_key_pem == null || cert_pem == null) {
            logger.error("cert_pem/cert_key_pem can not find")
            return false
        }

//        val cert = Base64.getDecoder().decode(AESUtil.decrypt(key, cert_pem))
//        val key = Base64.getDecoder().decode(AESUtil.decrypt(key, cert_key_pem))
//        val certFile = File.createTempFile("cert", ".pem")
//        val keyFile = File.createTempFile("key", ".pem")


        val sslContext = SSLContext.getInstance("SSL")
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
//        val certTrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
//        val certKeyTrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        val certKeyStore = saveCert()
        val certKeyKeyStore = saveCertKey()
        trustManagerFactory.init(certKeyStore)
        val trustManager = trustManagerFactory.trustManagers[0] as X509TrustManager
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(certKeyKeyStore, "cert_key".toCharArray())
        sslContext.init(keyManagerFactory.keyManagers, Array<TrustManager>(1) { trustManager }, SecureRandom())

        val client = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .build()

        val url =
            "https://tke.kubernetes.oa.com/v2/forward/stke/apis/apps/v1beta2/namespaces/$namespace/deployments/$appsName"
        val request = Request.Builder()
            .url(url)
            .addHeader("X-TKE-ClusterName", clusterName)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val data = response.body()!!.string()
            logger.info("aaaaaaaa: \n" + data)
        }
        return true
    }

    private fun saveCert(): KeyStore {

        val certFile = File.createTempFile("cert", ".pem")
        val cert = Base64.getDecoder().decode(AESUtil.decrypt(key, cert_pem!!))
        certFile.writeText(String(cert))
        certFile.deleteOnExit()

        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        val password: CharArray = "cert".toCharArray()

        var fis: FileInputStream? = null
        try {
            fis = FileInputStream(certFile)
            ks.load(fis, password)
        } finally {
            fis?.close()
        }
        return ks
    }

    private fun saveCertKey(): KeyStore {
        val certKeyFile = File.createTempFile("cert_key", ".pem")
        val certKey = Base64.getDecoder().decode(AESUtil.decrypt(key, cert_key_pem!!))
        certKeyFile.writeText(String(certKey))
        certKeyFile.deleteOnExit()

        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        val password: CharArray = "cert_key".toCharArray()

        var fis: FileInputStream? = null
        try {
            fis = FileInputStream(certKeyFile)
            ks.load(fis, password)
        } finally {
            fis?.close()
        }
        return ks
    }
}

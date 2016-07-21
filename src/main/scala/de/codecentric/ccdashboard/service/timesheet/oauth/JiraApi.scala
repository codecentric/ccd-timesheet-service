package de.codecentric.ccdashboard.service.timesheet.oauth

import java.security.spec.PKCS8EncodedKeySpec
import java.security.{KeyFactory, PrivateKey}
import java.util.Base64

import com.github.scribejava.core.builder.api.DefaultApi10a
import com.github.scribejava.core.model.OAuth1RequestToken
import com.github.scribejava.core.services.{RSASha1SignatureService, SignatureService}

/**
  * Created by bjacobs on 21.07.16.
  */
class JiraApi(val privateKey: String, val authorizeUrl: String, val requestTokenResource: String, val accessTokenResource: String) extends DefaultApi10a {
  def getAccessTokenEndpoint: String = accessTokenResource

  def getAuthorizationUrl(requestToken: OAuth1RequestToken): String = String.format(authorizeUrl, requestToken.getToken)

  def getRequestTokenEndpoint: String = requestTokenResource

  override def getSignatureService: SignatureService = new RSASha1SignatureService(getPrivateKey)

  private def getPrivateKey: PrivateKey = try {

    val key: Array[Byte] = Base64.getDecoder.decode(privateKey) // this is the PEM encoded PKCS#8 private key
    val keySpec: PKCS8EncodedKeySpec = new PKCS8EncodedKeySpec(key)
    val kf: KeyFactory = KeyFactory.getInstance("RSA")
    kf.generatePrivate(keySpec)
  }
  catch {
    case e: Exception => {
      throw new RuntimeException(e)
    }
  }
}

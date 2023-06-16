package bow.keycloak.resource;

import org.jboss.logging.Logger;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.resteasy.spi.HttpRequest;
import org.keycloak.authorization.util.Tokens;
import org.keycloak.common.ClientConnection;
import org.keycloak.events.Errors;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.representations.AccessToken;
import org.keycloak.services.ErrorResponseException;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resources.Cors;
import org.keycloak.utils.MediaType;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Base64;


public class MyResourceProvider implements RealmResourceProvider {

  private static final Logger logger = Logger.getLogger(MyResourceProvider.class);

  private final KeycloakSession keycloakSession;

  public MyResourceProvider(KeycloakSession session) {
    this.keycloakSession = session;
  }

  public void close() {
    // NOP
  }

  public Object getResource() {
    return this;
  }

  @OPTIONS
  @Path("{any:.*}")
  public Response preflight(){
    HttpRequest request = this.keycloakSession.getContext().getContextObject(HttpRequest.class);
    return Cors.add((org.keycloak.http.HttpRequest) request, Response.ok()).auth().preflight().build();
  }

  @GET
  @Path("init")
  @NoCache
  @Produces({ MediaType.APPLICATION_JSON })
  @Encoded
  public Response setSessionCookies(@QueryParam("client_id") String clientId,
                                    @QueryParam("response_type") String responseType,
                                    @QueryParam("redirect_uri") String redirectUri,
                                    @QueryParam("state") String state,
                                    @QueryParam("scope") String scope,
                                    @QueryParam("access_token") String accessToken) { // client เก็บ access token

    AccessToken validToken = this.validateAccessToken(accessToken); //============================================================

    // ClientModel newClient = this.getValidatedTargetClient(targetClient);
    final RealmModel realm = this.keycloakSession.getContext().getRealm();

    // create new user session and bind it to the target client Id
    final UserModel user = this.keycloakSession.users().getUserById(realm, validToken.getSubject());
    final ClientConnection clientConnection = this.keycloakSession.getContext().getConnection();
    UserSessionModel newUserSession = this.keycloakSession.sessions().createUserSession(realm, user, user.getUsername(), clientConnection.getRemoteAddr(), "KEYCLOAK", false, null, null);
    // this.keycloakSession.sessions().createClientSession(realm,
    // newClient, newUserSession);

    // finally create cookies
    final UriInfo uriInfo = this.keycloakSession.getContext().getUri();
    AuthenticationManager.createLoginCookie(this.keycloakSession, realm, newUserSession.getUser(), newUserSession, uriInfo, clientConnection);

//    System.out.println("SYSTEM OUT :::: " + realm.getName());
//    System.out.println("SYSTEM OUT :::: " + uriInfo.getAbsolutePath().toString());
//    System.out.println("SYSTEM OUT :::: " + uriInfo.getBaseUri().toString());

    // String accessControlAllowOrigin = this.getAccessControlAllowOrigin(targetClient);
    // String accessControlAllowOrigin = authServerRootUrl
    String targetUrl = String.format("%srealms/%s/protocol/openid-connect/auth?client_id=%s&response_type=%s&redirect_uri=%s&state=%s&scope=%s",
            uriInfo.getBaseUri().toString(), realm.getName(),clientId, responseType, redirectUri, state, scope);  //============================================================ ทำให้ no config
    // String targetUrl = "http://localhost:8080/realms/master/protocol/openid-connect/auth?client_id=%s?response_type=%s?redirect_uri=%s?state=%s?scope=%s";

//    System.out.println("SYSTEM OUT :::: " +targetUrl);

    URI targetAppUrl = URI.create(targetUrl);
    return Response
            .seeOther(targetAppUrl)
            .header("Access-Control-Allow-Credentials", "true")
            .build();
  }

  // private String getAccessControlAllowOrigin(String targetClient) {
  //   ClientModel newClient = this.getValidatedTargetClient(targetClient);
  //   // get referer
  //   String refererHeader = this.keycloakSession.getContext().getRequestHeaders().getHeaderString("Referer");
  //   String referer;
  //   try {
  //     URL url;
  //     url = new URL(refererHeader);
  //     String protocol = url.getProtocol();
  //     String authority = url.getAuthority();
  //     referer = String.format("%s://%s", protocol, authority);
  //   } catch (MalformedURLException e) {
  //     referer = "";
  //   }

  //   // search for matching web origin
  //   for (String currentWebOrigin : newClient.getWebOrigins()) {
  //     if (currentWebOrigin.equals("*")) {
  //       // `*` not allowed when `Access-Control-Allow-Credentials` is `true`
  //       return referer;
  //     }
  //     if (currentWebOrigin.equalsIgnoreCase(referer)) {
  //       return referer;
  //     }
  //   }

  //   // fail with empty one
  //   return "";
  // }

  // private ClientModel getValidatedTargetClient(String targetClient) {
  //   final RealmModel realm = this.keycloakSession.getContext().getRealm();
  //   ClientModel newClient = this.keycloakSession.clients().getClientByClientId(realm, targetClient);
  //   String clientInitSession = "initial_session";
  //   if (null == newClient) {
  //     throw new ErrorResponseException(Errors.CLIENT_NOT_FOUND, "Client not found",
  //         Response.Status.BAD_REQUEST);
  //   }
  //   return newClient;
  // }

  // public JsonElement parse(String json) throws JsonSyntaxException

  private static String decode(String encodedString) {
    return new String(Base64.getUrlDecoder().decode(encodedString));
  }

  private AccessToken validateAccessToken(String accessToken) {
    System.out.println("token:  " + accessToken);
    if (accessToken == null) {
      throw new ErrorResponseException(Errors.INVALID_TOKEN, "No authorization header provided",
              Response.Status.UNAUTHORIZED);
    }
    System.out.println("keycloakSession:  " + this.keycloakSession);
    // check client > client id = inintal_session
    // String[] chunks = accessToken.split("\\.");
    // Base64.Decoder decoder = Base64.getUrlDecoder();
    // String b64payload = chunks[1];
    // String jsonString = new String(decoder.decode(b64payload));
    // JsonObject convertedObject = new Gson().fromJson(jsonString, JsonObject.class);
    // String clientId = String.valueOf(convertedObject.get("azp"));

    // if (clientId != "init_session") {
    //   throw new ErrorResponseException(Errors.INVALID_TOKEN, "This client does not allow to initial session using access token.",
    //     Response.Status.UNAUTHORIZED);
    // }


    final AccessToken token = Tokens.getAccessToken(accessToken, this.keycloakSession);
    if (token == null) {
      throw new ErrorResponseException(Errors.INVALID_TOKEN, "Invalid or expired access token",
              Response.Status.UNAUTHORIZED);
    }
    return token;
  }
}

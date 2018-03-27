package com.blerter.token.grpc.service;

import java.security.InvalidKeyException;
import java.security.SignatureException;
import java.util.Calendar;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.lognet.springboot.grpc.GRpcService;
import org.springframework.beans.factory.annotation.Value;

import com.blerter.constant.Status;
import com.blerter.grpc.service.TokenServiceGrpc;
import com.blerter.model.Response;
import com.blerter.model.Token;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;

import io.grpc.stub.StreamObserver;
import net.oauth.jsontoken.JsonToken;
import net.oauth.jsontoken.JsonTokenParser;
import net.oauth.jsontoken.crypto.HmacSHA256Signer;
import net.oauth.jsontoken.crypto.HmacSHA256Verifier;
import net.oauth.jsontoken.crypto.SignatureAlgorithm;
import net.oauth.jsontoken.crypto.Verifier;
import net.oauth.jsontoken.discovery.VerifierProvider;
import net.oauth.jsontoken.discovery.VerifierProviders;

/**
 * Token grpc service
 *
 */
@GRpcService
public class GrpcTokenService extends TokenServiceGrpc.TokenServiceImplBase {

	private static Logger logger = LogManager.getLogger(GrpcTokenService.class);

	@Value("${jwt.audience}")
	private String jwtAudience;

	@Value("${jwt.issuer}")
	private String jwtIssuer;

	@Value("${jwt.signingKey}")
	private String jwtSigningKey;

	@Value("${jwt.durationInDays}")
	private String jwtDurationInDays;

	
	/*
	 * (non-Javadoc)
	 * @see com.xmdevelopments.sync.grpc.service.TokenServiceGrpc.TokenServiceImplBase#checkToken(com.xmdevelopments.sync.grpc.service.Token, io.grpc.stub.StreamObserver)
	 */
	@Override
	public void checkToken(Token request, StreamObserver<Response> responseObserver) {
		String prefix = "checkToken() ";

		Response.Builder responseBuilder = Response.newBuilder();
		try {
			boolean hasValidToken = hasValidToken(request.getToken());

			if (!hasValidToken) {
				responseBuilder.setResponseCode(Status.BadRequest.value());
				responseBuilder.setInfo("Token was not recognised or expired");
			} else {
				responseBuilder.setResponseCode(Status.Ok.value());
				responseBuilder.setInfo("Token is valid");

			}
		} catch (Exception exc) {
			responseBuilder.setResponseCode(Status.InternalServerError.value());
			responseBuilder.setInfo(exc.getMessage());
		}
		Response response = responseBuilder.build();
		responseObserver.onNext(response);
		responseObserver.onCompleted();
		logger.info(prefix + " {service-grpc} completed");
	}

	/*
	 * (non-Javadoc)
	 * @see com.xmdevelopments.sync.grpc.service.TokenServiceGrpc.TokenServiceImplBase#generateToken(com.xmdevelopments.sync.grpc.service.Token, io.grpc.stub.StreamObserver)
	 */
	@Override
	public void generateToken(Token request, StreamObserver<Response> responseObserver) {
		String prefix = "generateToken() ";

		Response.Builder responseBuilder = Response.newBuilder();

		try {
			String token = createJsonWebToken(request.getUserId(), Long.parseLong(jwtDurationInDays));
			if (token != null) {
				responseBuilder.setResponseCode(Status.Ok.value());
				responseBuilder.setInfo(token);
			} else {
				responseBuilder.setResponseCode(Status.BadRequest.value());
				responseBuilder.setInfo("Token error");

			}
		} catch (Exception exc) {
			responseBuilder.setResponseCode(Status.InternalServerError.value());
			responseBuilder.setInfo(exc.getMessage());
		}
		Response response = responseBuilder.build();
		responseObserver.onNext(response);
		responseObserver.onCompleted();
		logger.info(prefix + " {service-grpc} completed");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.blerter.grpc.service.MasterServiceGrpc.MasterServiceImplBase#ping(com.
	 * blerter.model.Empty, io.grpc.stub.StreamObserver)
	 */
	public void ping(com.blerter.model.Empty request,
			io.grpc.stub.StreamObserver<com.blerter.model.Response> responseObserver) {
		Response.Builder responseBuilder = Response.newBuilder();
		responseBuilder.setResponseCode(Status.Ok.value());
		Response response = responseBuilder.build();
		responseObserver.onNext(response);
		responseObserver.onCompleted();
	}
	
	/**
	 * Create json web token
	 * 
	 * @param userId
	 * @param durationDays
	 */
	private String createJsonWebToken(String userId, Long durationDays) {
		Calendar cal = Calendar.getInstance();
		HmacSHA256Signer signer;
		try {
			signer = new HmacSHA256Signer(jwtIssuer, null, jwtSigningKey.getBytes());
		} catch (InvalidKeyException e) {
			throw new RuntimeException(e);
		}

		// configure json token
		JsonToken token = new net.oauth.jsontoken.JsonToken(signer);
		token.setAudience(jwtAudience);
		token.setIssuedAt(new org.joda.time.Instant(cal.getTimeInMillis()));
		token.setExpiration(new org.joda.time.Instant(cal.getTimeInMillis() + 1000L * 60L * 60L * 24L * durationDays));

		// configure request object, which provides information of the item
		JsonObject request = new JsonObject();
		request.addProperty("userId", userId);

		JsonObject payload = token.getPayloadAsJsonObject();
		payload.add("info", request);

		try {
			return token.serializeAndSign();
		} catch (SignatureException sex) {
			throw new RuntimeException(sex);
		}
	}

	/**
	 * Has valid token
	 * 
	 * @param token
	 */
	private boolean hasValidToken(String token) {

		boolean hasValidToken = false;
		try {
			final Verifier hmacVerifier = new HmacSHA256Verifier(jwtSigningKey.getBytes());

			VerifierProvider hmacLocator = new VerifierProvider() {

				/**
				 * Find verifier
				 */
				@Override
				public List<Verifier> findVerifier(String id, String key) {
					return Lists.newArrayList(hmacVerifier);
				}
			};
			VerifierProviders locators = new VerifierProviders();
			locators.setVerifierProvider(SignatureAlgorithm.HS256, hmacLocator);
			net.oauth.jsontoken.Checker checker = new net.oauth.jsontoken.Checker() {

				/**
				 * Check
				 */
				@Override
				public void check(JsonObject payload) throws SignatureException {
				}

			};
			// ignore audience does not mean that the Signature is ignored
			JsonTokenParser parser = new JsonTokenParser(locators, checker);
			JsonToken jt;
			try {
				jt = parser.verifyAndDeserialize(token);
				JsonObject payload = jt.getPayloadAsJsonObject();
				String issuer = payload.getAsJsonPrimitive("iss").getAsString();
				String userIdString = payload.getAsJsonObject("info").getAsJsonPrimitive("userId").getAsString();
				if (issuer.equals(jwtIssuer) && !userIdString.isEmpty()) {
					hasValidToken = true;
					logger.info("----------------------------------------------------------");
					logger.info("userId:" + userIdString);
					logger.info("issued:" + new DateTime(payload.getAsJsonPrimitive("iat").getAsLong()));
					logger.info("expires:" + new DateTime(payload.getAsJsonPrimitive("exp").getAsLong()));
					logger.info("----------------------------------------------------------");
				}
			} catch (Exception ex) {
				logger.error(ex.getMessage());
			}

		} catch (Exception exc) {
			logger.error(exc.getMessage());
		}
		return hasValidToken;
	}
}



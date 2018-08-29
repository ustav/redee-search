package de.scout24.redee;

import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import is24.mapi.Api;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class SearchHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final String HEADERS_KEY = "headers";
	private static final String QUERY_KEY = "queryStringParameters";

	private static final Logger LOG = LogManager.getLogger(SearchHandler.class);

	private static final ObjectMapper objectMapper = new ObjectMapper();

	Api api = new Api(false);

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		LOG.info("received: {}", input);

		Map<String, String> headers = null;
		if (input.containsKey(HEADERS_KEY)) {
			headers = (Map<String, String>) input.get(HEADERS_KEY);
		}

		Map<String, String> queryParameters = null;
		if (input.containsKey(QUERY_KEY)) {
			queryParameters = (Map<String, String>) input.get(QUERY_KEY);
		}

		api.search(queryParameters, headers.get("Authorization"));

		Response responseBody = new Response("Go Serverless v1.x! Your function executed successfully!", input);
		return ApiGatewayResponse.builder()
				.setStatusCode(200)
				.setObjectBody(responseBody)
				.setHeaders(Collections.singletonMap("X-Powered-By", "AWS Lambda & serverless"))
				.build();
	}
}

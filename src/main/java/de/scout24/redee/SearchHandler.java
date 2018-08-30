package de.scout24.redee;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.scout24.redee.exception.ResourceException;
import com.scout24.redee.extraction.DateExtraction;
import com.scout24.redee.extraction.stanford.StanfordInformationExtractor;
import de.scout24.redee.model.SearchResultWithAppointments;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import is24.mapi.Api;
import is24.mapi.model.SearchPage;
import is24.mapi.model.SearchResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SearchHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

    private static final String QUERY_KEY = "queryStringParameters";
    private static final String HEADERS_KEY = "headers";
    private static final String APPOINTMENTS_QUERY_PARAM = "appointments";


    private static final Logger LOG = LogManager.getLogger(SearchHandler.class);

    private StanfordInformationExtractor extractor;
    private Api api;
    private Pattern clientResponseCodePattern;

    public SearchHandler() {
        api = new Api(false);
        clientResponseCodePattern = Pattern.compile("(?<responseCode>4\\d\\d)");
        try {
            extractor = new StanfordInformationExtractor();
        } catch (IOException | ResourceException e) {
            LOG.error("Error initializing text extractor: " + e.getMessage(), e);
            throw new RuntimeException("Error initializing text extractor: " + e.getMessage(), e);
        }
    }

    @Override
    public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
        LOG.info("received: {}", input);

        final Map<String, String> queryParameters = readMapFromInputAndCast(input, QUERY_KEY);
        final Map<String, String> headers = readMapFromInputAndCast(input, HEADERS_KEY);
        final String authBearer = headers.getOrDefault("Authorization", "Bearer foo");
        final boolean searchAppointments = Boolean.valueOf(queryParameters.getOrDefault(APPOINTMENTS_QUERY_PARAM, "false"));

        queryParameters.remove(APPOINTMENTS_QUERY_PARAM);

        try {
            final SearchPage response = searchAppointments ?
                    getResponseWithExtractedVisitDates(queryParameters, authBearer) :
                    getOriginalAPIResponse(queryParameters, authBearer);

            return ApiGatewayResponse.builder()
                    .setStatusCode(200)
                    .setObjectBody(response)
                    .build();
        } catch (Exception e) {
            String message = getErrorMessage(e);
            int responseCode = getErrorResponseCode(message);
            LOG.error("Exception occurred, returning " + responseCode + " to client. Message: " + message, e);
            return ApiGatewayResponse.builder()
                    .setStatusCode(responseCode)
                    .setObjectBody(message)
                    .build();
        }
    }

    private SearchPage getOriginalAPIResponse(Map<String, String> queryParameters, String authBearer) {
        return api.search(queryParameters, authBearer)
                .blockingGet();
    }

    private SearchPage getResponseWithExtractedVisitDates(Map<String, String> queryParameters, String authBearer) {
        queryParameters.put("pagesize", "201");
        return api.search(queryParameters, authBearer)
                .subscribeOn(Schedulers.io())
                .flatMap(searchPage ->
                        Flowable.fromIterable(searchPage.results)
                                .parallel()
                                .runOn(Schedulers.io())
                                .flatMap(searchResult -> extractAppointments(searchResult, authBearer).toFlowable())
                                .filter(searchResult -> !((SearchResultWithAppointments) searchResult).dates.isEmpty())
                                .sequential()
                                .toList()
                                .map(searchItemWithAppointments ->
                                        new SearchPage(
                                                searchItemWithAppointments.size(),
                                                searchItemWithAppointments.size(),
                                                1,
                                                1,
                                                searchItemWithAppointments.size(),
                                                searchPage.searchId,
                                                searchItemWithAppointments
                                        )
                                ))
                .blockingGet();
    }

    private Single<SearchResult> extractAppointments(SearchResult searchResult, String authBearer) {
        return api.getLegacyExpose(searchResult.id, authBearer).map(legacyExpose -> {
            String title = legacyExpose.realEstate.title;
            String otherNote = legacyExpose.realEstate.otherNote;
            List<DateExtraction> extractions = new ArrayList<>();

            Date now = new Date();

            if (title != null) {
                Collection<DateExtraction> titleExtractions = extractor.extract(legacyExpose.realEstate.title);
                for (DateExtraction titleExtraction : titleExtractions) {
                    if (titleExtraction.getStart().after(now)) {
                        extractions.add(titleExtraction);
                    }
                }
            }

            if (otherNote != null) {
                Collection<DateExtraction> otherExtractions = extractor.extract(legacyExpose.realEstate.otherNote);
                for (DateExtraction otherExtraction : otherExtractions) {
                    if (otherExtraction.getStart().after(now)) {
                        extractions.add(otherExtraction);
                    }
                }
            }

            return new SearchResultWithAppointments(
                    searchResult.id,
                    searchResult.reportUrl,
                    searchResult.isProject,
                    searchResult.isPrivate,
                    searchResult.listingType,
                    searchResult.published,
                    searchResult.isNewObject,
                    searchResult.pictures,
                    searchResult.titlePicture,
                    searchResult.address,
                    searchResult.attributes,
                    searchResult.projectObjectsSectionHeading,
                    searchResult.projectObjects,
                    searchResult.groupingObjectsSectionHeading,
                    searchResult.groupingObjects,
                    extractions,
                    legacyExpose.realEstate.otherNote,
                    legacyExpose.realEstate.title
            );
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> readMapFromInputAndCast(Map<String, Object> input, String key) {
        final Map<String, String> retVal = (Map<String, String>) input.get(key);
        if (retVal == null) {
            return Collections.emptyMap();
        } else {
            return retVal;
        }
    }

    private String getErrorMessage(Throwable e) {
        if (e.getCause() != null && e.getCause().getMessage() != null) {
            return e.getCause().getMessage();
        } else if (e.getMessage() != null) {
            return e.getMessage();
        }
        return "An error has occurred.";
    }

    private int getErrorResponseCode(String message) {
        if (message.toLowerCase().contains("oauthtoken")) {
            return 401;
        }
        Matcher matcher = clientResponseCodePattern.matcher(message);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group("responseCode"));
        } else {
            return 500;
        }
    }
}

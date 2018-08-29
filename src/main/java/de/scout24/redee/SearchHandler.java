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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.*;

public class SearchHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

    private static final String QUERY_KEY = "queryStringParameters";
    private static final String HEADERS_KEY = "headers";
    private static final String APPOINTMENTS_QUERY_PARAM = "appointments";

    private static final Logger LOG = LogManager.getLogger(SearchHandler.class);

    private StanfordInformationExtractor extractor;
    private Api api;

    public SearchHandler() {
        api = new Api(false);
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
        } catch (RuntimeException e) {
            LOG.error("Exception occurred, returning 500 to client. Message: " + e.getMessage(), e);
            return ApiGatewayResponse.builder()
                    .setStatusCode(500)
                    .setObjectBody(e.getMessage())
                    .build();
        }
    }

    private SearchPage getOriginalAPIResponse(Map<String, String> queryParameters, String authBearer) {
        return api.search(queryParameters, authBearer)
                .doOnError(this::errorHandler)
                .blockingGet();
    }

    private SearchPage getResponseWithExtractedVisitDates(Map<String, String> queryParameters, String authBearer) {
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
                                                searchPage.totalResults,
                                                searchPage.pageSize,
                                                searchPage.pageNumber,
                                                searchPage.numberOfPages,
                                                searchPage.numberOfListings,
                                                searchPage.searchId,
                                                searchItemWithAppointments
                                        )
                                ))
                .doOnError(this::errorHandler)
                .blockingGet();
    }

    private Single<SearchResult> extractAppointments(SearchResult searchResult, String authBearer) {
        return api.getLegacyExpose(searchResult.id, authBearer).map(legacyExpose -> {
            String otherNote = legacyExpose.realEstate.otherNote;
            String title = legacyExpose.realEstate.title;
            List<DateExtraction> extractions = new ArrayList<>();

            if (otherNote != null) {
                Collection<DateExtraction> otherExtractions = extractor.extract(legacyExpose.realEstate.otherNote);
                extractions.addAll(otherExtractions);
            }

            if (title != null) {
                Collection<DateExtraction> titleExtractions = extractor.extract(legacyExpose.realEstate.title);
                extractions.addAll(titleExtractions);
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

    private void errorHandler(Throwable throwable) {
        final String message = "Server error has occurred, message: " + throwable.getMessage();

        LOG.error(message, throwable);
        throw new RuntimeException(message, throwable);
    }
}

package de.scout24.redee;

import java.io.IOException;
import java.util.*;

import com.scout24.redee.exception.ResourceException;
import com.scout24.redee.extraction.DateExtraction;
import com.scout24.redee.extraction.stanford.StanfordInformationExtractor;
import de.scout24.redee.model.SearchItemWithAppointments;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import is24.mapi.Api;
import is24.mapi.model.SearchItem;
import is24.mapi.model.SearchResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class SearchHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

    private static final String HEADERS_KEY = "headers";
    private static final String QUERY_KEY = "queryStringParameters";

    private static final Logger LOG = LogManager.getLogger(SearchHandler.class);

    private StanfordInformationExtractor extractor;

    Api api = new Api(false);

    @Override
    public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
        LOG.info("received: {}", input);

        try {
            extractor = new StanfordInformationExtractor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ResourceException e) {
            e.printStackTrace();
        }

        Map<String, String> headers = null;
        if (input.containsKey(HEADERS_KEY)) {
            headers = (Map<String, String>) input.get(HEADERS_KEY);
        }

        Map<String, String> queryParameters = null;
        if (input.containsKey(QUERY_KEY)) {
            queryParameters = (Map<String, String>) input.get(QUERY_KEY);
        }

        String authBearer = headers.get("Authorization");

        SearchResponse response = api.search(queryParameters, authBearer)
                .subscribeOn(Schedulers.io())
                .flatMap(searchResponse ->
                        Flowable.fromIterable(searchResponse.results)
                                .parallel()
                                .runOn(Schedulers.io())
                                .flatMap(searchItem -> extractAppointments(searchItem, authBearer).toFlowable())
                                .filter(searchItem -> !((SearchItemWithAppointments)searchItem).dates.isEmpty())
                                .sequential()
                                .toList()
                                .map(searchItemWithAppointments ->
                                        new SearchResponse(
                                                searchItemWithAppointments,
                                                searchResponse.totalResults,
                                                searchResponse.pageSize,
                                                searchResponse.pageNumber,
                                                searchResponse.numberOfPages,
                                                searchResponse.totalNewResults
                                        )
                                ))
                .blockingGet();


        return ApiGatewayResponse.builder()
                .setStatusCode(200)
                .setObjectBody(response)
                .build();
    }

    private Single<SearchItem> extractAppointments(SearchItem searchItem, String authBearer) {
        return api.getLegacyExpose(searchItem.id, authBearer).map(legacyExpose -> {
            String otherNote = legacyExpose.realEstate.otherNote;
            String title =legacyExpose.realEstate.title;

            List<DateExtraction> extractions = new ArrayList<>();
            if (otherNote != null) {
                Collection<DateExtraction> otherExtractions = extractor.extract(legacyExpose.realEstate.otherNote);
                extractions.addAll(otherExtractions);
            }

            if (title != null) {
                Collection<DateExtraction> titleExtractions = extractor.extract(legacyExpose.realEstate.title);
                extractions.addAll(titleExtractions);
            }

//            DateExtraction dateExtraction = new DateExtraction(new Date(),new Date(), legacyExpose.realEstate.otherNote, "", Position.createEmptyPosition());
            return new SearchItemWithAppointments(searchItem.id, searchItem.infoLine, searchItem.attributes, searchItem.pictureUrl, extractions, legacyExpose.realEstate.otherNote, legacyExpose.realEstate.title);
        });
    }


}

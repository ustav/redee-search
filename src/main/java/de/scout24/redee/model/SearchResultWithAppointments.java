package de.scout24.redee.model;

import com.scout24.redee.extraction.DateExtraction;
import is24.mapi.model.*;

import java.util.List;

public class SearchResultWithAppointments extends SearchResult {
    public final List<DateExtraction> dates;
    public final String otherNote;
    public final String title;


    public SearchResultWithAppointments(String id, String reportUrl, boolean isProject, boolean isPrivate, String listingType, String published, boolean isNewObject, List<SearchResultPicture> pictures, SearchResultTitlePicture titlePicture, SearchResultAddress address, List<SearchResultAttribute> attributes, String projectObjectsSectionHeading, List<SearchResult> projectObjects, String groupingObjectsSectionHeading, List<SearchResult> groupingObjects, List<DateExtraction> dates, String otherNote, String title) {
        super(id, reportUrl, isProject, isPrivate, listingType, published, isNewObject, pictures, titlePicture, address, attributes, projectObjectsSectionHeading, projectObjects, groupingObjectsSectionHeading, groupingObjects);
        this.dates = dates;
        this.otherNote = otherNote;
        this.title = title;
    }
}

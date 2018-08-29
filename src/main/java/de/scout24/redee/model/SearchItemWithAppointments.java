package de.scout24.redee.model;

import com.scout24.redee.extraction.DateExtraction;
import is24.mapi.model.SearchItem;
import java.util.List;

public class SearchItemWithAppointments extends SearchItem {

    public final List<DateExtraction> dates;
    public final String otherNote;
    public final String title;

    public SearchItemWithAppointments(String id, String infoLine, List<String> attributes, String pictureUrl, List<DateExtraction> dates, String otherNote, String title) {
        super(id, infoLine, attributes, pictureUrl);
        this.dates = dates;
        this.otherNote = otherNote;
        this.title = title;
    }

    @Override
    public String toString() {
        return "SearchItemWithAppointments{" +
                "dates=" + dates +
                ", id='" + id + '\'' +
                ", infoLine='" + infoLine + '\'' +
                ", attributes=" + attributes +
                ", pictureUrl='" + pictureUrl + '\'' +
                '}';
    }
}

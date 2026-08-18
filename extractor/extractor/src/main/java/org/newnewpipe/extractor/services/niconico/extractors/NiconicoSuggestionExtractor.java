package org.newnewpipe.extractor.services.niconico.extractors;

import org.newnewpipe.extractor.NewPipe;
import org.newnewpipe.extractor.StreamingService;
import org.newnewpipe.extractor.exceptions.ExtractionException;
import org.newnewpipe.extractor.services.niconico.NiconicoService;
import org.newnewpipe.extractor.suggestion.SuggestionExtractor;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import static org.newnewpipe.extractor.utils.Utils.UTF_8;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

public class NiconicoSuggestionExtractor extends SuggestionExtractor {
    public NiconicoSuggestionExtractor(final StreamingService service) {
        super(service);
    }

    @Override
    public List<String> suggestionList(final String query)
            throws IOException, ExtractionException {
        final List<String> suggestions = new ArrayList<>();
        final String encoded = URLEncoder.encode(query, UTF_8);
        final String response = NewPipe.getDownloader()
                .get(NiconicoService.SUGGESTION_URL + encoded).responseBody();
        try {
            final JsonArray jsonArray = JsonParser.object().from(response).getArray("candidates");
            for (int i = 0; i < jsonArray.size(); i++) {
                suggestions.add(jsonArray.getString(i));
            }
        } catch (final JsonParserException e) {
            throw new ExtractionException("could not parse search suggestions.");
        }

        return suggestions;
    }
}

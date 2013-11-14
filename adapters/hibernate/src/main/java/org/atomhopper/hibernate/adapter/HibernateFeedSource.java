package org.atomhopper.hibernate.adapter;

import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.abdera.Abdera;
import static org.apache.abdera.i18n.text.UrlEncoding.decode;
import static org.apache.abdera.i18n.text.UrlEncoding.encode;
import org.apache.abdera.model.Document;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;
import org.apache.abdera.model.Link;
import org.apache.commons.lang.StringUtils;
import org.atomhopper.adapter.FeedInformation;
import org.atomhopper.adapter.FeedSource;
import org.atomhopper.adapter.NotImplemented;
import org.atomhopper.adapter.ResponseBuilder;
import org.atomhopper.adapter.jpa.PersistedEntry;
import org.atomhopper.adapter.jpa.PersistedFeed;
import org.atomhopper.adapter.AdapterHelper;
import org.atomhopper.adapter.request.adapter.GetEntryRequest;
import org.atomhopper.adapter.request.adapter.GetFeedRequest;
import org.atomhopper.dbal.FeedRepository;
import org.atomhopper.dbal.PageDirection;
import org.atomhopper.hibernate.query.SimpleCategoryCriteriaGenerator;
import org.atomhopper.response.AdapterResponse;
import org.atomhopper.util.uri.template.EnumKeyedTemplateParameters;
import org.atomhopper.util.uri.template.URITemplate;

public class HibernateFeedSource implements FeedSource {

    private static final int PAGE_SIZE = 25;
    private FeedRepository feedRepository;
    private AdapterHelper helper = new AdapterHelper();

    public void setFeedRepository(FeedRepository feedRepository) {
        this.feedRepository = feedRepository;
    }

    @Override
    public FeedInformation getFeedInformation() {
        return new HibernateFeedInformation(feedRepository);
    }

    @Override
    @NotImplemented
    public void setParameters(Map<String, String> params) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

   private void addFeedSelfLink(Feed feed, final String baseFeedUri,
            final GetFeedRequest getFeedRequest,
            final int pageSize, final String searchString) {

        StringBuilder queryParams = new StringBuilder();
        boolean markerIsSet = false;

        queryParams.append(baseFeedUri).append("?limit=").append(String.valueOf(pageSize));

        if(searchString.length() > 0) {
            queryParams.append("&search=").append(urlEncode(searchString).toString());
        }
        if(getFeedRequest.getPageMarker() != null && getFeedRequest.getPageMarker().length() > 0) {
                queryParams.append("&marker=").append(getFeedRequest.getPageMarker());
                markerIsSet = true;
        }
        if(markerIsSet) {
            queryParams.append("&direction=").append(getFeedRequest.getDirection());
        } else {
            queryParams.append("&direction=backward");
            if(queryParams.toString().equalsIgnoreCase(baseFeedUri + "?limit=" + pageSize + "&direction=backward")) {
                // They are calling the feedhead, just use the base feed uri
                // This keeps the validator at http://validator.w3.org/ happy
                queryParams.delete(0, queryParams.toString().length()).append(baseFeedUri);
            }
        }
        feed.addLink(queryParams.toString()).setRel(Link.REL_SELF);
    }

    private void addFeedCurrentLink(Feed hyrdatedFeed, final String baseFeedUri) {

        String url = helper.isArchived() ? helper.getCurrentUrl() : baseFeedUri;

        hyrdatedFeed.addLink( url, Link.REL_CURRENT);
    }

    private Feed hydrateFeed(Abdera abdera, List<PersistedEntry> persistedEntries, GetFeedRequest getFeedRequest, final int pageSize) {
        final Feed hydratedFeed = abdera.newFeed();
        final String uuidUriScheme = "urn:uuid:";
        final String baseFeedUri = decode(getFeedRequest.urlFor(new EnumKeyedTemplateParameters<URITemplate>(URITemplate.FEED)));
        final String searchString = getFeedRequest.getSearchQuery() != null ? getFeedRequest.getSearchQuery() : "";

        if ( helper.isArchived() ) {

            helper.addArchiveNode( hydratedFeed );
        }

        // Set the feed links
        addFeedCurrentLink(hydratedFeed, baseFeedUri);
        addFeedSelfLink(hydratedFeed, baseFeedUri, getFeedRequest, pageSize, searchString);

        PersistedEntry nextPersistedEntry = null;

        // TODO: We should have a link builder method for these
        if (!(persistedEntries.isEmpty())) {
            hydratedFeed.setId( uuidUriScheme + UUID.randomUUID().toString() );
            hydratedFeed.setTitle( getFeedRequest.getFeedName().toString() );

            // Set the previous link
            hydratedFeed.addLink( new StringBuilder()
                                        .append( baseFeedUri ).append( "?marker=" )
                                        .append( persistedEntries.get( 0 ).getEntryId() )
                                        .append( "&limit=" )
                                        .append( String.valueOf( pageSize ) )
                                        .append( "&search=" )
                                        .append( urlEncode( searchString ).toString() )
                                        .append( "&direction=forward" ).toString() )
                    .setRel( helper.getPrevLink() );


            nextPersistedEntry = feedRepository.getNextMarker(persistedEntries.get(persistedEntries.size() - 1),
                    getFeedRequest.getFeedName().toString(), new SimpleCategoryCriteriaGenerator(searchString));

            // If limit > actual number of entries in the database, there
            // is not a next link
            if (nextPersistedEntry != null) {
                // Set the next link
                hydratedFeed.addLink( new StringBuilder()
                                            .append( baseFeedUri )
                                            .append( "?marker=" )
                                            .append( nextPersistedEntry.getEntryId() )
                                            .append( "&limit=" )
                                            .append( String.valueOf( pageSize ) )
                                            .append( "&search=" )
                                            .append( urlEncode( searchString ).toString() )
                                            .append( "&direction=backward" ).toString() ).setRel( helper.getNextLink() );
            }
        }

        // if we are at the last page & there is an archive link, provide it
        if ( nextPersistedEntry == null && helper.getArchiveUrl() != null ) {
            hydratedFeed.addLink(new StringBuilder().append( helper.getArchiveUrl() ).append( "?limit=" ).append(String.valueOf(pageSize))
                                       .append( "&direction=backward" ).toString())
                  .setRel( FeedSource.REL_ARCHIVE_NEXT );
        }


        for (PersistedEntry persistedFeedEntry : persistedEntries) {
            hydratedFeed.addEntry( hydrateEntry( persistedFeedEntry, abdera ) );
        }

        return hydratedFeed;
    }

    private Entry hydrateEntry(PersistedEntry persistedEntry, Abdera abderaReference) {
        final Document<Entry> hydratedEntryDocument = abderaReference.getParser().parse(new StringReader(persistedEntry.getEntryBody()));
        Entry entry = null;

        if (hydratedEntryDocument != null) {
            entry = hydratedEntryDocument.getRoot();

            entry.setUpdated(persistedEntry.getDateLastUpdated());
            entry.setPublished(persistedEntry.getCreationDate());
        }

        return entry;
    }

    @Override
    public AdapterResponse<Entry> getEntry(GetEntryRequest getEntryRequest) {
        final PersistedEntry entry = feedRepository.getEntry(getEntryRequest.getEntryId(), getEntryRequest.getFeedName());
        AdapterResponse<Entry> response = ResponseBuilder.notFound();

        if (entry != null) {
            response = ResponseBuilder.found(hydrateEntry(entry, getEntryRequest.getAbdera()));
        }

        return response;
    }

    @Override
    public void setArchiveUrl( URL url ) {

        helper.setArchiveUrl( url );
    }


    @Override
    public void setCurrentUrl( URL urlCurrent ) {

        helper.setCurrentUrl( urlCurrent );
    }

    @Override
    public AdapterResponse<Feed> getFeed(GetFeedRequest getFeedRequest) {
        AdapterResponse<Feed> response;

        int pageSize = PAGE_SIZE;
        final String pageSizeString = getFeedRequest.getPageSize();

        if (StringUtils.isNotBlank(pageSizeString)) {
            pageSize = Integer.parseInt(pageSizeString);
        }

        final String marker = getFeedRequest.getPageMarker();

        if (StringUtils.isNotBlank(marker)) {
            response = getFeedPage(getFeedRequest, marker, pageSize);
        } else {
            response = getFeedHead(getFeedRequest, getFeedRequest.getFeedName(), pageSize);
        }

        return response;
    }

    private AdapterResponse<Feed> getFeedHead(GetFeedRequest getFeedRequest, String feedName, int pageSize) {
        final Abdera abdera = getFeedRequest.getAbdera();
        final PersistedFeed persistedFeed = feedRepository.getFeed(feedName);
        AdapterResponse<Feed> response = null;

        if (persistedFeed != null) {
            final String searchString = getFeedRequest.getSearchQuery() != null ? getFeedRequest.getSearchQuery() : "";
            final List<PersistedEntry> persistedEntries = feedRepository.getFeedHead(feedName, new SimpleCategoryCriteriaGenerator(searchString), pageSize);

            Feed hyrdatedFeed = hydrateFeed(abdera, persistedEntries, getFeedRequest, pageSize);
            // Set the last link in the feed head
            final String baseFeedUri = decode(getFeedRequest.urlFor(new EnumKeyedTemplateParameters<URITemplate>(URITemplate.FEED)));

            final List<PersistedEntry> lastPersistedEntries = feedRepository.getLastPage(feedName, pageSize, new SimpleCategoryCriteriaGenerator(searchString));

            if (!helper.isArchived() && lastPersistedEntries != null && !(lastPersistedEntries.isEmpty())) {
                hyrdatedFeed.addLink(new StringBuilder()
                        .append(baseFeedUri)
                        .append("?marker=")
                        .append(lastPersistedEntries.get(lastPersistedEntries.size() - 1).getEntryId())
                        .append("&limit=")
                        .append(String.valueOf(pageSize))
                        .append("&search=")
                        .append(urlEncode(searchString).toString())
                        .append("&direction=backward").toString())
                        .setRel(Link.REL_LAST);
            }

            response = ResponseBuilder.found(hyrdatedFeed);
        }

        return response != null ? response : ResponseBuilder.found(abdera.newFeed());
    }

    private AdapterResponse<Feed> getFeedPage(GetFeedRequest getFeedRequest, String marker, int pageSize) {
        AdapterResponse<Feed> response;
        PageDirection pageDirection;

        try {
            final String pageDirectionValue = getFeedRequest.getDirection();
            pageDirection = PageDirection.valueOf(pageDirectionValue.toUpperCase());
        } catch (Exception iae) {
            return ResponseBuilder.badRequest("Marker must have a page direction specified as either \"forward\" or \"backward\"");
        }

        final PersistedEntry markerEntry = feedRepository.getEntry(marker, getFeedRequest.getFeedName());

        if (markerEntry != null) {
            final String searchString = getFeedRequest.getSearchQuery() != null ? getFeedRequest.getSearchQuery() : "";
            final Feed feed = hydrateFeed(getFeedRequest.getAbdera(),
                    feedRepository.getFeedPage(getFeedRequest.getFeedName(), markerEntry, pageDirection,
                    new SimpleCategoryCriteriaGenerator(searchString), pageSize), getFeedRequest, pageSize);

            response = ResponseBuilder.found(feed);
        } else {
            response = ResponseBuilder.notFound("No entry with specified marker found");
        }

        return response;
    }

    private String urlEncode(String searchString)  {
        try {
            return URLEncoder.encode(searchString, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            //noop - should never get here
            return "";
        }
    }
}

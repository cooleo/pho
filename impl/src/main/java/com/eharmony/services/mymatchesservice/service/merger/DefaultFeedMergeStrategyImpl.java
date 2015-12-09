package com.eharmony.services.mymatchesservice.service.merger;

import static com.eharmony.services.mymatchesservice.service.transform.MatchFeedModel.PROFILE.BIRTHDATE;
import static com.eharmony.services.mymatchesservice.service.transform.MatchFeedModel.PROFILE.CITY;
import static com.eharmony.services.mymatchesservice.service.transform.MatchFeedModel.PROFILE.COUNTRY;
import static com.eharmony.services.mymatchesservice.service.transform.MatchFeedModel.PROFILE.FIRSTNAME;
import static com.eharmony.services.mymatchesservice.service.transform.MatchFeedModel.PROFILE.GENDER;
import static com.eharmony.services.mymatchesservice.service.transform.MatchFeedModel.PROFILE.STATE_CODE;
import static com.eharmony.services.mymatchesservice.service.transform.MatchFeedModel.PROFILE.USERID;
import static com.eharmony.services.mymatchesservice.service.transform.MatchFeedModel.SECTIONS.PROFILE;

import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eharmony.datastore.model.MatchDataFeedItemDto;
import com.eharmony.datastore.model.MatchElement;
import com.eharmony.datastore.model.MatchProfileElement;
import com.eharmony.services.mymatchesservice.rest.MatchFeedRequestContext;
import com.eharmony.services.mymatchesservice.service.UserMatchesHBaseStoreFeedService;
import com.eharmony.services.mymatchesservice.store.LegacyMatchDataFeedDto;

public class DefaultFeedMergeStrategyImpl implements FeedMergeStrategy {

    private static final Logger log = LoggerFactory.getLogger(DefaultFeedMergeStrategyImpl.class);

    @Override
    public void merge(MatchFeedRequestContext requestContext, UserMatchesHBaseStoreFeedService userMatchesFeedService) {

        // no need to merge for a fallback request because the hbase feed has already been converted into legacy feed.
        if (requestContext.isFallbackRequest()) {
            return;
        }

        log.info("merging feed for userId {}", requestContext.getUserId());
        LegacyMatchDataFeedDto legacyMatchesFeed = requestContext.getLegacyMatchDataFeedDto();
        Set<MatchDataFeedItemDto> storeMatchesFeed = requestContext.getNewStoreFeed();

        if (CollectionUtils.isEmpty(storeMatchesFeed)) {
            if (legacyMatchesFeed != null && MapUtils.isNotEmpty(legacyMatchesFeed.getMatches())) {
                log.warn("There are no matches in HBase for user {} and found {} matches in voldy",
                        requestContext.getUserId(), legacyMatchesFeed.getMatches().size());
            } else {
                log.info("no matches found for user {} in both hbase and voldy", requestContext.getUserId());
            }
        } else if (legacyMatchesFeed != null && MapUtils.isNotEmpty(legacyMatchesFeed.getMatches())) {
            Map<String, Map<String, Map<String, Object>>> matches = legacyMatchesFeed.getMatches();
            mergeHBaseProfileIntoMatchFeed(matches, storeMatchesFeed);

        } else {
            log.error(
                    "{} Records exist in HBase for user {}, none in voldy. Using FULL HBase record.This path must not be exeucted.",
                    storeMatchesFeed.size(), requestContext.getUserId());
        }
    }

    private void mergeHBaseProfileIntoMatchFeed(Map<String, Map<String, Map<String, Object>>> matches,
            Set<MatchDataFeedItemDto> hbaseFeed) {

        for (MatchDataFeedItemDto hbaseMatch : hbaseFeed) {

            String matchId = Long.toString(hbaseMatch.getMatch().getMatchId());
            Map<String, Map<String, Object>> feedMatch = matches.get(matchId);
            if (feedMatch == null) {

                log.info(
                        "HBase match {} not found in voldy feed for user {} during merge and keeping volde as source of truth",
                        matchId, hbaseMatch.getMatch().getUserId());
                continue;
            }
            Map<String, Object> feedProfile = feedMatch.get(PROFILE);

            // overwrite feed with HBase values
            MatchProfileElement profile = hbaseMatch.getMatchedUser();

            MatchElement matchElement = hbaseMatch.getMatch();
            long matchedUserId = matchElement.getMatchedUserId();
            if (profile.getGender() > 0) {
                feedProfile.put(GENDER, profile.getGender());
            } else {
                log.info("Gender must not be null in HBase for user {} and match {}", matchedUserId, matchId);
            }
            if (profile.getCountry() > 0) {
                feedProfile.put(COUNTRY, profile.getCountry());
            } else {
                log.info("Country must not be null in HBase for user {} and match {}", matchedUserId, matchId);
            }
            feedProfile.put(USERID, matchedUserId);
            if (StringUtils.isNotBlank(profile.getCity())) {
                feedProfile.put(CITY, profile.getCity());
            } else {
                log.info("city must not be blank in HBase for user {} and match {}", matchedUserId, matchId);
            }
            if (StringUtils.isNotBlank(profile.getFirstName())) {
                feedProfile.put(FIRSTNAME, profile.getFirstName());
            } else {
                log.info("firstname must not be blank in HBase for user {} and match {}", matchedUserId, matchId);
            }
            if (StringUtils.isNotBlank(profile.getStateCode())) {
                feedProfile.put(STATE_CODE, profile.getStateCode());
            }
            if (profile.getBirthdate() != null) {
                feedProfile.put(BIRTHDATE, profile.getBirthdate().getTime());
            } else {
                log.info("birthdate must not be null in HBase for user {} and match {}", matchedUserId, matchId);
            }

        }
    }
}
/*
 * Copyright (C) 2016 Appflate.io
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.appflate.restmock;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import kotlin.Pair;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

class MatchableCallsRequestDispatcher extends Dispatcher {

    private List<MatchableCall> matchableCalls;
    private List<RecordedRequest> requestsHistory = new CopyOnWriteArrayList<>();

    // Traded RAM for less hassle with transformations
    private List<Pair<RecordedRequest, MockResponse>> requestResponseHistory =
            new CopyOnWriteArrayList<>();

    public MatchableCallsRequestDispatcher() {
        matchableCalls = new CopyOnWriteArrayList<>();
    }

    @Override
    public MockResponse dispatch(RecordedRequest recordedRequest) throws InterruptedException {
        requestsHistory.add(recordedRequest);
        RESTMockServer.getLogger().log("-> New Request:\t" + recordedRequest);
        List<MatchableCall> matchedCalls = getMatchedRequests(recordedRequest);

        MockResponse mockResponse;
        if (matchedCalls.size() == 1) {
            mockResponse = onOneResponseMatched(recordedRequest, matchedCalls);
        } else if (matchedCalls.size() > 1) {
            mockResponse = onTooManyResponsesMatched(recordedRequest, matchedCalls);
        } else {
            mockResponse = onNoResponsesMatched(recordedRequest);
        }

        requestResponseHistory.add(new Pair<>(recordedRequest, mockResponse));

        return mockResponse;
    }

    private MockResponse onOneResponseMatched(RecordedRequest recordedRequest, List<MatchableCall> matchedRequests) {
        MockResponse response = matchedRequests.get(0).nextResponse(recordedRequest);
        RESTMockServer.getLogger().log("<- Response:\t" + response);
        return response;
    }

    private MockResponse onTooManyResponsesMatched(RecordedRequest recordedRequest, List<MatchableCall> matchedRequests) {
        String message = prepareTooManyMatchesMessage(recordedRequest, matchedRequests);
        RESTMockServer.getLogger().error("<- Response ERROR:\t" + message);
        return createErrorResponse(new IllegalStateException(message));
    }

    private MockResponse onNoResponsesMatched(RecordedRequest recordedRequest) {
        RESTMockServer.getLogger()
            .error("<- Response ERROR:\t"
                + RESTMockServer.RESPONSE_NOT_MOCKED
                + ": "
                + recordedRequest
                + "\n list of mocked requests:\n"
                + prepareAllMocksMessage());
        return createNotMockedResponse(recordedRequest.getMethod());
    }

    private String prepareAllMocksMessage() {
        StringBuilder sb = new StringBuilder();
        for (MatchableCall match : matchableCalls) {
            sb.append(match.requestMatcher.toString()).append("\n");
        }
        return sb.toString();
    }

    private MockResponse createNotMockedResponse(String httpMethod) {
        MockResponse mockResponse = new MockResponse().setResponseCode(500);
        if (!httpMethod.equals("HEAD")) {
            mockResponse.setBody(RESTMockServer.RESPONSE_NOT_MOCKED);
        }
        return mockResponse;
    }

    private String prepareTooManyMatchesMessage(RecordedRequest recordedRequest, final List<MatchableCall> matchedRequests) {
        StringBuilder sb = new StringBuilder(RESTMockServer.MORE_THAN_ONE_RESPONSE_ERROR + recordedRequest + ": ");
        for (MatchableCall match : matchedRequests) {
            sb.append(match.requestMatcher.toString()).append("\n");
        }
        return sb.toString();
    }

    private List<MatchableCall> getMatchedRequests(RecordedRequest recordedRequest) {
        List<MatchableCall> matched = new LinkedList<>();
        for (MatchableCall request : matchableCalls) {
            if (request.requestMatcher.matches(recordedRequest)) {
                matched.add(request);
            }
        }
        return matched;
    }

    MockResponse createErrorResponse(Exception e) {
        MockResponse response = new MockResponse();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        response.setBody(sw.toString());
        response.setResponseCode(500);
        return response;
    }

    void addMatchableCall(MatchableCall matchableCall) {
        if (matchableCall.getNumberOfAnswers() > 0) {
            RESTMockServer.getLogger().log("## Adding new response for:\t" + matchableCall.requestMatcher);
            if (!matchableCalls.contains(matchableCall)) {
                matchableCalls.add(matchableCall);
            }
        } else {
            RESTMockServer.getLogger().log("## There were no responses specified for MatchableCall:\t" + matchableCall.requestMatcher);
        }
    }

    void removeAllMatchableCalls() {
        RESTMockServer.getLogger().log("## Removing all responses");
        matchableCalls.clear();
    }

    boolean removeMatchableCall(final MatchableCall call) {
        RESTMockServer.getLogger().log("## Removing response for:\t" + call.requestMatcher);
        return matchableCalls.remove(call);
    }

    List<RecordedRequest> getRequestHistory() {
        return new ArrayList<>(requestsHistory);
    }

    List<Pair<RecordedRequest, MockResponse>> getRequestResponseHistory() {
        return new ArrayList<>(requestResponseHistory);
    }

    void clearHistoricalRequests() {
        requestsHistory.clear();
        requestResponseHistory.clear();
    }
}
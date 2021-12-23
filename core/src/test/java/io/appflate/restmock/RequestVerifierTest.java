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

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static io.appflate.restmock.RequestsVerifier.verifyDELETE;
import static io.appflate.restmock.RequestsVerifier.verifyGET;
import static io.appflate.restmock.RequestsVerifier.verifyPOST;
import static io.appflate.restmock.RequestsVerifier.verifyPUT;
import static io.appflate.restmock.RequestsVerifier.verifyRequest;
import static io.appflate.restmock.utils.RequestMatchers.pathEndsWith;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.appflate.restmock.exceptions.RequestInvocationCountMismatchException;
import io.appflate.restmock.exceptions.RequestInvocationCountNotEnoughException;
import io.appflate.restmock.exceptions.RequestNotInvokedException;
import io.appflate.restmock.utils.RequestMatchers;
import io.appflate.restmock.utils.TestUtils;
import kotlin.Pair;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Created by andrzejchm on 26/04/16.
 */
@RunWith(Parameterized.class)
public class RequestVerifierTest {

    private static final String path = "sample";
    private static final Matcher<RecordedRequest> INVOKED_MATCHER = pathEndsWith(path);
    private static final Matcher<RecordedRequest> NOT_INVOKED_MATCHER = pathEndsWith("else");

    private final boolean useHttps;

    @Parameterized.Parameters(name = "useHttps={0}")
    public static Collection<Object> data() {
        return Arrays.asList(new Object[]{
                true, false
        });
    }

    public RequestVerifierTest(boolean useHttps) {
        this.useHttps = useHttps;
    }

    @Before
    public void setup() {
        RESTMockFileParser fileParser = mock(RESTMockFileParser.class);
        RESTMockServerStarter.startSync(fileParser, new RESTMockOptions.Builder().useHttps(useHttps).build());
        RESTMockServer.dispatcher = spy(RESTMockServer.dispatcher);
    }

    @After
    public void teardown() throws IOException {
        RESTMockServer.shutdown();
    }

    @Test
    public void testValidAssertions() throws Exception {
        RESTMockServer.whenRequested(pathEndsWith(path)).thenReturnString("a single call");
        verifyRequest(INVOKED_MATCHER).never();
        verifyRequest(INVOKED_MATCHER).exactly(0);
        TestUtils.get(path);
        verifyRequest(INVOKED_MATCHER).invoked();
        verifyRequest(INVOKED_MATCHER).exactly(1);
        TestUtils.get(path);
        verifyRequest(INVOKED_MATCHER).exactly(2);
        TestUtils.get(path);
        verifyRequest(INVOKED_MATCHER).exactly(3);
        verifyRequest(INVOKED_MATCHER).atLeast(1);
        verifyRequest(INVOKED_MATCHER).atLeast(2);
        verifyRequest(INVOKED_MATCHER).atLeast(3);
    }

    @Test
    public void testHTTPMethodVerifier() throws Exception {
        RESTMockServer.whenRequested(pathEndsWith(path)).thenReturnString("a single call");
        verifyRequest(INVOKED_MATCHER).never();
        verifyRequest(INVOKED_MATCHER).exactly(0);
        TestUtils.get(path);
        TestUtils.post(path);
        TestUtils.post(path);
        TestUtils.put(path);
        TestUtils.put(path);
        TestUtils.put(path);
        TestUtils.delete(path);
        TestUtils.delete(path);
        TestUtils.delete(path);
        TestUtils.delete(path);
        verifyRequest(INVOKED_MATCHER).exactly(10);
        verifyGET(INVOKED_MATCHER).exactly(1);
        verifyPOST(INVOKED_MATCHER).exactly(2);
        verifyPUT(INVOKED_MATCHER).exactly(3);
        verifyDELETE(INVOKED_MATCHER).exactly(4);
    }

    @Test
    public void takesLastNumOfElementsWhenHistoryNotEmpty() throws Exception {
        RESTMockServer.whenRequested(pathEndsWith(path)).thenReturnString("a single call");
        TestUtils.get(path);
        TestUtils.post(path);
        TestUtils.delete(path);
        TestUtils.head(path);

        List<RecordedRequest> recordedRequests = RequestsVerifier.takeLast(3);
        assertEquals(3, recordedRequests.size());
        assertEquals("POST", Objects.requireNonNull(recordedRequests.get(0).getMethod()).toUpperCase(Locale.US));
        assertEquals("DELETE", Objects.requireNonNull(recordedRequests.get(1).getMethod()).toUpperCase(Locale.US));
        assertEquals("HEAD", Objects.requireNonNull(recordedRequests.get(2).getMethod()).toUpperCase(Locale.US));
    }

    @Test
    public void takeLastNumOfElementsExceedsHistorySizeTakesWholeHistory() throws Exception {
        RESTMockServer.whenRequested(pathEndsWith(path)).thenReturnString("a single call");
        TestUtils.get(path);
        TestUtils.post(path);
        TestUtils.delete(path);
        TestUtils.head(path);

        List<RecordedRequest> recordedRequests = RequestsVerifier.takeLast(10);
        assertEquals(4, recordedRequests.size());
        assertEquals("GET", Objects.requireNonNull(recordedRequests.get(0).getMethod()).toUpperCase(Locale.US));
        assertEquals("POST", Objects.requireNonNull(recordedRequests.get(1).getMethod()).toUpperCase(Locale.US));
        assertEquals("DELETE", Objects.requireNonNull(recordedRequests.get(2).getMethod()).toUpperCase(Locale.US));
        assertEquals("HEAD", Objects.requireNonNull(recordedRequests.get(3).getMethod()).toUpperCase(Locale.US));
    }

    @Test(expected = IllegalArgumentException.class)
    public void takeLastNumOfElementsWithInvalidCountThrowsException() throws Exception {
        RESTMockServer.whenRequested(pathEndsWith(path)).thenReturnString("a single call");
        TestUtils.get(path);
        TestUtils.post(path);
        TestUtils.delete(path);
        TestUtils.head(path);

        RequestsVerifier.takeLast(-10);
    }

    @Test
    public void takesFirstNumOfElementsWhenHistoryNotEmpty() throws Exception {
        RESTMockServer.whenRequested(pathEndsWith(path)).thenReturnString("a single call");
        TestUtils.get(path);
        TestUtils.post(path);
        TestUtils.delete(path);
        TestUtils.head(path);

        List<RecordedRequest> recordedRequests = RequestsVerifier.takeFirst(3);
        assertEquals(3, recordedRequests.size());
        assertEquals("GET", Objects.requireNonNull(recordedRequests.get(0).getMethod()).toUpperCase(Locale.US));
        assertEquals("POST", Objects.requireNonNull(recordedRequests.get(1).getMethod()).toUpperCase(Locale.US));
        assertEquals("DELETE", Objects.requireNonNull(recordedRequests.get(2).getMethod()).toUpperCase(Locale.US));
    }

    @Test
    public void takeFirstNumOfElementsExceedsHistorySizeTakesAllHistory() throws Exception {
        RESTMockServer.whenRequested(pathEndsWith(path)).thenReturnString("a single call");
        TestUtils.get(path);
        TestUtils.post(path);
        TestUtils.delete(path);
        TestUtils.head(path);

        List<RecordedRequest> recordedRequests = RequestsVerifier.takeFirst(10);
        assertEquals(4, recordedRequests.size());
        assertEquals("GET", Objects.requireNonNull(recordedRequests.get(0).getMethod()).toUpperCase(Locale.US));
        assertEquals("POST", Objects.requireNonNull(recordedRequests.get(1).getMethod()).toUpperCase(Locale.US));
        assertEquals("DELETE", Objects.requireNonNull(recordedRequests.get(2).getMethod()).toUpperCase(Locale.US));
        assertEquals("HEAD", Objects.requireNonNull(recordedRequests.get(3).getMethod()).toUpperCase(Locale.US));
    }

    @Test
    public void takesSubsetOfRequests() throws Exception {
        RESTMockServer.whenRequested(pathEndsWith(path)).thenReturnString("a single call");
        TestUtils.get(path);
        TestUtils.post(path);
        TestUtils.delete(path);
        TestUtils.head(path);

        List<RecordedRequest> recordedRequests = RequestsVerifier.take(1, 4);
        assertEquals(3, recordedRequests.size());
        assertEquals("POST", Objects.requireNonNull(recordedRequests.get(0).getMethod()).toUpperCase(Locale.US));
        assertEquals("DELETE", Objects.requireNonNull(recordedRequests.get(1).getMethod()).toUpperCase(Locale.US));
        assertEquals("HEAD", Objects.requireNonNull(recordedRequests.get(2).getMethod()).toUpperCase(Locale.US));
    }

    @Test(expected = IllegalArgumentException.class)
    public void takesSubsetOfRequestsWithInvalidRangeThrowsError() throws Exception {
        RESTMockServer.whenRequested(pathEndsWith(path)).thenReturnString("a single call");
        TestUtils.get(path);
        TestUtils.post(path);
        TestUtils.delete(path);
        TestUtils.head(path);

        RequestsVerifier.take(5, 3);
    }

    @Test
    public void takeMatchingFindsAllRelevantRequests() throws Exception {
        RESTMockServer.whenRequested(pathEndsWith(path)).thenReturnString("a single call");
        TestUtils.get(path);
        TestUtils.post(path);
        TestUtils.delete(path);
        TestUtils.get(path);
        TestUtils.head(path);
        TestUtils.get(path);
        List<RecordedRequest> recordedRequests = RequestsVerifier.takeAllMatching(RequestMatchers.isGET());
        assertEquals(3, recordedRequests.size());
        assertEquals("GET", Objects.requireNonNull(recordedRequests.get(0).getMethod()).toUpperCase(Locale.US));
        assertEquals("GET", Objects.requireNonNull(recordedRequests.get(1).getMethod()).toUpperCase(Locale.US));
        assertEquals("GET", Objects.requireNonNull(recordedRequests.get(2).getMethod()).toUpperCase(Locale.US));
    }

    @Test
    public void takeMatchingFindsAllRelevantRequestPairs() throws Exception {
        String payload1 = "a single call1";
        String payload2 = "a single call2";
        String payload3 = "a single call3";

        RESTMockServer.whenRequested(pathEndsWith(path))
                .thenReturnString(payload1, payload2, payload3);

        TestUtils.get(path); // this will get payload1, because it's first in a row
        TestUtils.post(path); // this will get payload2, because it's 2nd in a row
        TestUtils.delete(path); // this will get payload 3
        TestUtils.get(path); // this will also get payload 3, by design of serving mocks
        TestUtils.head(path); // this will also get payload 3, see above
        TestUtils.get(path); // this will also get payload 3, see above

        List<Pair<RecordedRequest, MockResponse>> recordedPairs = RequestsVerifier
                .takeAllMatchingPairs(RequestMatchers.isGET());

        assertEquals(3, recordedPairs.size());

        assertEquals("GET", Objects.requireNonNull(recordedPairs.get(0).getFirst()
                .getMethod()).toUpperCase(Locale.US));
        assertEquals(payload1, Objects.requireNonNull(recordedPairs.get(0).getSecond()
                .getBody()).readUtf8());

        assertEquals("GET", Objects.requireNonNull(recordedPairs.get(1).getFirst()
                .getMethod()).toUpperCase(Locale.US));
        assertEquals(payload3, Objects.requireNonNull(recordedPairs.get(1).getSecond()
                .getBody()).readUtf8());

        assertEquals("GET", Objects.requireNonNull(recordedPairs.get(2).getFirst()
                .getMethod()).toUpperCase(Locale.US));
        assertEquals(payload3, Objects.requireNonNull(recordedPairs.get(2).getSecond()
                .getBody()).readUtf8());
    }

    @Test(expected = IllegalArgumentException.class)
    public void takeFirstNumOfElementsWithInvalidCountThrowsException() throws Exception {
        RESTMockServer.whenRequested(pathEndsWith(path)).thenReturnString("a single call");
        TestUtils.get(path);
        TestUtils.post(path);
        TestUtils.delete(path);
        TestUtils.head(path);

        RequestsVerifier.takeFirst(-10);
    }

    @Test
    public void takeSingleFirstWhenNoHistoryIsNull() {
        RESTMockServer.whenRequested(pathEndsWith(path)).thenReturnString("a single call");
        assertNull(RequestsVerifier.takeFirst());
    }

    @Test
    public void takeSingleLastWhenNoHistoryIsNull() {
        RESTMockServer.whenRequested(pathEndsWith(path)).thenReturnString("a single call");
        assertNull(RequestsVerifier.takeLast());
    }

    @Test
    public void testReset() throws Exception {
        RESTMockServer.whenRequested(pathEndsWith(path)).thenReturnString("a single call");
        TestUtils.get(path);
        verifyRequest(INVOKED_MATCHER).exactly(1);
        verifyRequest(INVOKED_MATCHER).invoked();
        TestUtils.get(path);
        TestUtils.get(path);
        TestUtils.get(path);
        verifyRequest(INVOKED_MATCHER).exactly(4);
        RESTMockServer.reset();
        verifyRequest(INVOKED_MATCHER).never();
        TestUtils.get(path);
        verifyRequest(INVOKED_MATCHER).invoked();
    }

    @Test(expected = RequestNotInvokedException.class)
    public void testInvoked_NotInvokedException() {
        verifyRequest(NOT_INVOKED_MATCHER).invoked();
    }

    @Test(expected = RequestInvocationCountMismatchException.class)
    public void testInvoked_InvocationCountMismatchException() throws Exception {
        RESTMockServer.whenRequested(INVOKED_MATCHER).thenReturnString("a single call");
        TestUtils.get(path);
        verifyRequest(INVOKED_MATCHER).exactly(3);
    }

    @Test(expected = RequestInvocationCountNotEnoughException.class)
    public void testAtLeast_InvocationCountMismatchException() throws Exception {
        RESTMockServer.whenRequested(INVOKED_MATCHER).thenReturnString("a single call");
        RESTMockServer.whenRequested(INVOKED_MATCHER).thenReturnString("a single call");
        TestUtils.get(path);
        TestUtils.get(path + "something");
        TestUtils.get(path + "else");
        TestUtils.get(path);
        verifyRequest(INVOKED_MATCHER).atLeast(3);
    }

    @Test(expected = RequestNotInvokedException.class)
    public void testExactly_NotInvokedException() {
        verifyRequest(NOT_INVOKED_MATCHER).exactly(3);
    }

    @Test(expected = RequestInvocationCountMismatchException.class)
    public void testExactly_MismatchException() throws Exception {
        RESTMockServer.whenRequested(INVOKED_MATCHER).thenReturnString("a single call");
        TestUtils.get(path);
        verifyRequest(INVOKED_MATCHER).exactly(3);
    }
}

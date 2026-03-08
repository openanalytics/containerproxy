package eu.openanalytics.containerproxy.backend.spcs.client.api;

import eu.openanalytics.containerproxy.backend.spcs.client.ApiClient;

import eu.openanalytics.containerproxy.backend.spcs.client.model.CancelStatus;
import eu.openanalytics.containerproxy.backend.spcs.client.model.QueryFailureStatus;
import eu.openanalytics.containerproxy.backend.spcs.client.model.QueryStatus;
import eu.openanalytics.containerproxy.backend.spcs.client.model.ResultSet;
import eu.openanalytics.containerproxy.backend.spcs.client.model.SubmitStatementRequest;
import java.util.UUID;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2026-03-02T16:41:35.116838100+08:00[Australia/Perth]", comments = "Generator version: 7.17.0")
public class StatementsApi {
    private ApiClient apiClient;

    public StatementsApi() {
        this(new ApiClient());
    }

    public StatementsApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Cancels the execution of a statement.
     * Cancels the execution of the statement with the specified statement handle.
     * <p><b>200</b> - The execution of the statement was successfully canceled.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This happens if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint is wrong. For example, if the application hits /api/api/hello which doesn&#39;t exist, it will receive this code.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST. The application must change a method for retry.
     * <p><b>422</b> - An error occurred when cancelling the execution of the statement. Check the error code and error message for details.
     * <p><b>500</b> - Internal Server Error. The server hits an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended. https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended. https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     * @param statementHandle The handle of the statement that you want to use (e.g. to fetch the result set or cancel execution).
     * @param userAgent Set this to the name and version of your application (e.g. “applicationName/applicationVersion”). You must use a value that complies with RFC 7231.
     * @param requestId Unique ID of the API request. This ensures that the execution is idempotent. If not specified, a new UUID is generated and assigned.
     * @param accept The response payload format. The schema should be specified in resultSetMetaData in the request payload.
     * @param xSnowflakeAuthorizationTokenType Specify the authorization token type for the Authorization header. KEYPAIR_JWT is for Keypair JWT or OAUTH for oAuth token. If not specified, OAUTH is assumed.
     * @return CancelStatus
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec cancelStatementRequestCreation(@javax.annotation.Nonnull UUID statementHandle, @javax.annotation.Nonnull String userAgent, @javax.annotation.Nullable UUID requestId, @javax.annotation.Nullable String accept, @javax.annotation.Nullable String xSnowflakeAuthorizationTokenType) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'statementHandle' is set
        if (statementHandle == null) {
            throw new WebClientResponseException("Missing the required parameter 'statementHandle' when calling cancelStatement", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'userAgent' is set
        if (userAgent == null) {
            throw new WebClientResponseException("Missing the required parameter 'userAgent' when calling cancelStatement", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("statementHandle", statementHandle);

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "requestId", requestId));

        if (accept != null)
        headerParams.add("Accept", apiClient.parameterToString(accept));
        if (userAgent != null)
        headerParams.add("User-Agent", apiClient.parameterToString(userAgent));
        if (xSnowflakeAuthorizationTokenType != null)
        headerParams.add("X-Snowflake-Authorization-Token-Type", apiClient.parameterToString(xSnowflakeAuthorizationTokenType));
        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] { "ExternalOAuth", "KeyPair", "SnowflakeOAuth" };

        ParameterizedTypeReference<CancelStatus> localVarReturnType = new ParameterizedTypeReference<CancelStatus>() {};
        return apiClient.invokeAPI("/api/v2/statements/{statementHandle}/cancel", HttpMethod.POST, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Cancels the execution of a statement.
     * Cancels the execution of the statement with the specified statement handle.
     * <p><b>200</b> - The execution of the statement was successfully canceled.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This happens if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint is wrong. For example, if the application hits /api/api/hello which doesn&#39;t exist, it will receive this code.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST. The application must change a method for retry.
     * <p><b>422</b> - An error occurred when cancelling the execution of the statement. Check the error code and error message for details.
     * <p><b>500</b> - Internal Server Error. The server hits an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended. https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended. https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     * @param statementHandle The handle of the statement that you want to use (e.g. to fetch the result set or cancel execution).
     * @param userAgent Set this to the name and version of your application (e.g. “applicationName/applicationVersion”). You must use a value that complies with RFC 7231.
     * @param requestId Unique ID of the API request. This ensures that the execution is idempotent. If not specified, a new UUID is generated and assigned.
     * @param accept The response payload format. The schema should be specified in resultSetMetaData in the request payload.
     * @param xSnowflakeAuthorizationTokenType Specify the authorization token type for the Authorization header. KEYPAIR_JWT is for Keypair JWT or OAUTH for oAuth token. If not specified, OAUTH is assumed.
     * @return CancelStatus
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<CancelStatus> cancelStatement(@javax.annotation.Nonnull UUID statementHandle, @javax.annotation.Nonnull String userAgent, @javax.annotation.Nullable UUID requestId, @javax.annotation.Nullable String accept, @javax.annotation.Nullable String xSnowflakeAuthorizationTokenType) throws WebClientResponseException {
        ParameterizedTypeReference<CancelStatus> localVarReturnType = new ParameterizedTypeReference<CancelStatus>() {};
        return cancelStatementRequestCreation(statementHandle, userAgent, requestId, accept, xSnowflakeAuthorizationTokenType).bodyToMono(localVarReturnType);
    }

    /**
     * Cancels the execution of a statement.
     * Cancels the execution of the statement with the specified statement handle.
     * <p><b>200</b> - The execution of the statement was successfully canceled.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This happens if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint is wrong. For example, if the application hits /api/api/hello which doesn&#39;t exist, it will receive this code.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST. The application must change a method for retry.
     * <p><b>422</b> - An error occurred when cancelling the execution of the statement. Check the error code and error message for details.
     * <p><b>500</b> - Internal Server Error. The server hits an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended. https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended. https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     * @param statementHandle The handle of the statement that you want to use (e.g. to fetch the result set or cancel execution).
     * @param userAgent Set this to the name and version of your application (e.g. “applicationName/applicationVersion”). You must use a value that complies with RFC 7231.
     * @param requestId Unique ID of the API request. This ensures that the execution is idempotent. If not specified, a new UUID is generated and assigned.
     * @param accept The response payload format. The schema should be specified in resultSetMetaData in the request payload.
     * @param xSnowflakeAuthorizationTokenType Specify the authorization token type for the Authorization header. KEYPAIR_JWT is for Keypair JWT or OAUTH for oAuth token. If not specified, OAUTH is assumed.
     * @return ResponseEntity&lt;CancelStatus&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<CancelStatus>> cancelStatementWithHttpInfo(@javax.annotation.Nonnull UUID statementHandle, @javax.annotation.Nonnull String userAgent, @javax.annotation.Nullable UUID requestId, @javax.annotation.Nullable String accept, @javax.annotation.Nullable String xSnowflakeAuthorizationTokenType) throws WebClientResponseException {
        ParameterizedTypeReference<CancelStatus> localVarReturnType = new ParameterizedTypeReference<CancelStatus>() {};
        return cancelStatementRequestCreation(statementHandle, userAgent, requestId, accept, xSnowflakeAuthorizationTokenType).toEntity(localVarReturnType);
    }

    /**
     * Cancels the execution of a statement.
     * Cancels the execution of the statement with the specified statement handle.
     * <p><b>200</b> - The execution of the statement was successfully canceled.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This happens if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint is wrong. For example, if the application hits /api/api/hello which doesn&#39;t exist, it will receive this code.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST. The application must change a method for retry.
     * <p><b>422</b> - An error occurred when cancelling the execution of the statement. Check the error code and error message for details.
     * <p><b>500</b> - Internal Server Error. The server hits an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended. https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended. https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     * @param statementHandle The handle of the statement that you want to use (e.g. to fetch the result set or cancel execution).
     * @param userAgent Set this to the name and version of your application (e.g. “applicationName/applicationVersion”). You must use a value that complies with RFC 7231.
     * @param requestId Unique ID of the API request. This ensures that the execution is idempotent. If not specified, a new UUID is generated and assigned.
     * @param accept The response payload format. The schema should be specified in resultSetMetaData in the request payload.
     * @param xSnowflakeAuthorizationTokenType Specify the authorization token type for the Authorization header. KEYPAIR_JWT is for Keypair JWT or OAUTH for oAuth token. If not specified, OAUTH is assumed.
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec cancelStatementWithResponseSpec(@javax.annotation.Nonnull UUID statementHandle, @javax.annotation.Nonnull String userAgent, @javax.annotation.Nullable UUID requestId, @javax.annotation.Nullable String accept, @javax.annotation.Nullable String xSnowflakeAuthorizationTokenType) throws WebClientResponseException {
        return cancelStatementRequestCreation(statementHandle, userAgent, requestId, accept, xSnowflakeAuthorizationTokenType);
    }

    /**
     * Checks the status of the execution of a statement
     * Checks the status of the execution of the statement with the specified statement handle. If the statement was executed successfully, the operation returns the requested partition of the result set.
     * <p><b>200</b> - The statement was executed successfully, and the response includes any data requested.
     * <p><b>202</b> - The execution of the statement is still in progress. Use this method again to check the status of the statement execution.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This happens if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint is wrong. For example, if the application hits /api/api/hello which doesn&#39;t exist, it will receive this code.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST. The application must change a method for retry.
     * <p><b>415</b> - The request header Content-Type includes unsupported media type. The API supports application/json only. If none specified, the request payload is taken as JSON, but if any other media type is specified, this error is returned.
     * <p><b>422</b> - An error occurred when executing the statement. Check the error code and error message for details.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hits an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended. https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended. https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     * @param statementHandle The handle of the statement that you want to use (e.g. to fetch the result set or cancel execution).
     * @param userAgent Set this to the name and version of your application (e.g. “applicationName/applicationVersion”). You must use a value that complies with RFC 7231.
     * @param requestId Unique ID of the API request. This ensures that the execution is idempotent. If not specified, a new UUID is generated and assigned.
     * @param partition Number of the partition of results to return. The number can range from 0 to the total number of partitions minus 1.
     * @param accept The response payload format. The schema should be specified in resultSetMetaData in the request payload.
     * @param xSnowflakeAuthorizationTokenType Specify the authorization token type for the Authorization header. KEYPAIR_JWT is for Keypair JWT or OAUTH for oAuth token. If not specified, OAUTH is assumed.
     * @return ResultSet
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec getStatementStatusRequestCreation(@javax.annotation.Nonnull UUID statementHandle, @javax.annotation.Nonnull String userAgent, @javax.annotation.Nullable UUID requestId, @javax.annotation.Nullable Long partition, @javax.annotation.Nullable String accept, @javax.annotation.Nullable String xSnowflakeAuthorizationTokenType) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'statementHandle' is set
        if (statementHandle == null) {
            throw new WebClientResponseException("Missing the required parameter 'statementHandle' when calling getStatementStatus", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'userAgent' is set
        if (userAgent == null) {
            throw new WebClientResponseException("Missing the required parameter 'userAgent' when calling getStatementStatus", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("statementHandle", statementHandle);

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "requestId", requestId));
        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "partition", partition));

        if (accept != null)
        headerParams.add("Accept", apiClient.parameterToString(accept));
        if (userAgent != null)
        headerParams.add("User-Agent", apiClient.parameterToString(userAgent));
        if (xSnowflakeAuthorizationTokenType != null)
        headerParams.add("X-Snowflake-Authorization-Token-Type", apiClient.parameterToString(xSnowflakeAuthorizationTokenType));
        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] { "ExternalOAuth", "KeyPair", "SnowflakeOAuth" };

        ParameterizedTypeReference<ResultSet> localVarReturnType = new ParameterizedTypeReference<ResultSet>() {};
        return apiClient.invokeAPI("/api/v2/statements/{statementHandle}", HttpMethod.GET, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Checks the status of the execution of a statement
     * Checks the status of the execution of the statement with the specified statement handle. If the statement was executed successfully, the operation returns the requested partition of the result set.
     * <p><b>200</b> - The statement was executed successfully, and the response includes any data requested.
     * <p><b>202</b> - The execution of the statement is still in progress. Use this method again to check the status of the statement execution.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This happens if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint is wrong. For example, if the application hits /api/api/hello which doesn&#39;t exist, it will receive this code.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST. The application must change a method for retry.
     * <p><b>415</b> - The request header Content-Type includes unsupported media type. The API supports application/json only. If none specified, the request payload is taken as JSON, but if any other media type is specified, this error is returned.
     * <p><b>422</b> - An error occurred when executing the statement. Check the error code and error message for details.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hits an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended. https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended. https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     * @param statementHandle The handle of the statement that you want to use (e.g. to fetch the result set or cancel execution).
     * @param userAgent Set this to the name and version of your application (e.g. “applicationName/applicationVersion”). You must use a value that complies with RFC 7231.
     * @param requestId Unique ID of the API request. This ensures that the execution is idempotent. If not specified, a new UUID is generated and assigned.
     * @param partition Number of the partition of results to return. The number can range from 0 to the total number of partitions minus 1.
     * @param accept The response payload format. The schema should be specified in resultSetMetaData in the request payload.
     * @param xSnowflakeAuthorizationTokenType Specify the authorization token type for the Authorization header. KEYPAIR_JWT is for Keypair JWT or OAUTH for oAuth token. If not specified, OAUTH is assumed.
     * @return ResultSet
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResultSet> getStatementStatus(@javax.annotation.Nonnull UUID statementHandle, @javax.annotation.Nonnull String userAgent, @javax.annotation.Nullable UUID requestId, @javax.annotation.Nullable Long partition, @javax.annotation.Nullable String accept, @javax.annotation.Nullable String xSnowflakeAuthorizationTokenType) throws WebClientResponseException {
        ParameterizedTypeReference<ResultSet> localVarReturnType = new ParameterizedTypeReference<ResultSet>() {};
        return getStatementStatusRequestCreation(statementHandle, userAgent, requestId, partition, accept, xSnowflakeAuthorizationTokenType).bodyToMono(localVarReturnType);
    }

    /**
     * Checks the status of the execution of a statement
     * Checks the status of the execution of the statement with the specified statement handle. If the statement was executed successfully, the operation returns the requested partition of the result set.
     * <p><b>200</b> - The statement was executed successfully, and the response includes any data requested.
     * <p><b>202</b> - The execution of the statement is still in progress. Use this method again to check the status of the statement execution.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This happens if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint is wrong. For example, if the application hits /api/api/hello which doesn&#39;t exist, it will receive this code.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST. The application must change a method for retry.
     * <p><b>415</b> - The request header Content-Type includes unsupported media type. The API supports application/json only. If none specified, the request payload is taken as JSON, but if any other media type is specified, this error is returned.
     * <p><b>422</b> - An error occurred when executing the statement. Check the error code and error message for details.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hits an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended. https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended. https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     * @param statementHandle The handle of the statement that you want to use (e.g. to fetch the result set or cancel execution).
     * @param userAgent Set this to the name and version of your application (e.g. “applicationName/applicationVersion”). You must use a value that complies with RFC 7231.
     * @param requestId Unique ID of the API request. This ensures that the execution is idempotent. If not specified, a new UUID is generated and assigned.
     * @param partition Number of the partition of results to return. The number can range from 0 to the total number of partitions minus 1.
     * @param accept The response payload format. The schema should be specified in resultSetMetaData in the request payload.
     * @param xSnowflakeAuthorizationTokenType Specify the authorization token type for the Authorization header. KEYPAIR_JWT is for Keypair JWT or OAUTH for oAuth token. If not specified, OAUTH is assumed.
     * @return ResponseEntity&lt;ResultSet&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ResultSet>> getStatementStatusWithHttpInfo(@javax.annotation.Nonnull UUID statementHandle, @javax.annotation.Nonnull String userAgent, @javax.annotation.Nullable UUID requestId, @javax.annotation.Nullable Long partition, @javax.annotation.Nullable String accept, @javax.annotation.Nullable String xSnowflakeAuthorizationTokenType) throws WebClientResponseException {
        ParameterizedTypeReference<ResultSet> localVarReturnType = new ParameterizedTypeReference<ResultSet>() {};
        return getStatementStatusRequestCreation(statementHandle, userAgent, requestId, partition, accept, xSnowflakeAuthorizationTokenType).toEntity(localVarReturnType);
    }

    /**
     * Checks the status of the execution of a statement
     * Checks the status of the execution of the statement with the specified statement handle. If the statement was executed successfully, the operation returns the requested partition of the result set.
     * <p><b>200</b> - The statement was executed successfully, and the response includes any data requested.
     * <p><b>202</b> - The execution of the statement is still in progress. Use this method again to check the status of the statement execution.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This happens if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint is wrong. For example, if the application hits /api/api/hello which doesn&#39;t exist, it will receive this code.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST. The application must change a method for retry.
     * <p><b>415</b> - The request header Content-Type includes unsupported media type. The API supports application/json only. If none specified, the request payload is taken as JSON, but if any other media type is specified, this error is returned.
     * <p><b>422</b> - An error occurred when executing the statement. Check the error code and error message for details.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hits an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended. https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended. https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     * @param statementHandle The handle of the statement that you want to use (e.g. to fetch the result set or cancel execution).
     * @param userAgent Set this to the name and version of your application (e.g. “applicationName/applicationVersion”). You must use a value that complies with RFC 7231.
     * @param requestId Unique ID of the API request. This ensures that the execution is idempotent. If not specified, a new UUID is generated and assigned.
     * @param partition Number of the partition of results to return. The number can range from 0 to the total number of partitions minus 1.
     * @param accept The response payload format. The schema should be specified in resultSetMetaData in the request payload.
     * @param xSnowflakeAuthorizationTokenType Specify the authorization token type for the Authorization header. KEYPAIR_JWT is for Keypair JWT or OAUTH for oAuth token. If not specified, OAUTH is assumed.
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec getStatementStatusWithResponseSpec(@javax.annotation.Nonnull UUID statementHandle, @javax.annotation.Nonnull String userAgent, @javax.annotation.Nullable UUID requestId, @javax.annotation.Nullable Long partition, @javax.annotation.Nullable String accept, @javax.annotation.Nullable String xSnowflakeAuthorizationTokenType) throws WebClientResponseException {
        return getStatementStatusRequestCreation(statementHandle, userAgent, requestId, partition, accept, xSnowflakeAuthorizationTokenType);
    }

    /**
     * Submits a SQL statement for execution.
     * Submits one or more statements for execution. You can specify that the statement should be executed asynchronously.
     * <p><b>200</b> - The statement was executed successfully, and the response includes any data requested.
     * <p><b>202</b> - The execution of the statement is still in progress. Use GET /statements/ and specify the statement handle to check the status of the statement execution.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This happens if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint is wrong. For example, if the application hits /api/api/hello which doesn&#39;t exist, it will receive this code.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST. The application must change a method for retry.
     * <p><b>408</b> - The execution of the statement exceeded the timeout period. The execution of the statement was cancelled.
     * <p><b>415</b> - The request header Content-Type includes unsupported media type. The API supports application/json only. If none specified, the request payload is taken as JSON, but if any other media type is specified, this error is returned.
     * <p><b>422</b> - An error occurred when executing the statement. Check the error code and error message for details.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hits an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended. https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended. https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     * @param userAgent Set this to the name and version of your application (e.g. “applicationName/applicationVersion”). You must use a value that complies with RFC 7231.
     * @param submitStatementRequest Specifies the SQL statement to execute and the statement context.
     * @param requestId Unique ID of the API request. This ensures that the execution is idempotent. If not specified, a new UUID is generated and assigned.
     * @param async Set to true to execute the statement asynchronously and return the statement handle. If the parameter is not specified or is set to false, a statement is executed and the first result is returned if the execution is completed in 45 seconds. If the statement execution takes longer to complete, the statement handle is returned.
     * @param nullable Set to true to execute the statement to generate the result set including null. If the parameter is set to false, the result set value null will be replaced with a string &#39;null&#39;.
     * @param accept The response payload format. The schema should be specified in resultSetMetaData in the request payload.
     * @param xSnowflakeAuthorizationTokenType Specify the authorization token type for the Authorization header. KEYPAIR_JWT is for Keypair JWT or OAUTH for oAuth token. If not specified, OAUTH is assumed.
     * @return ResultSet
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec submitStatementRequestCreation(@javax.annotation.Nonnull String userAgent, @javax.annotation.Nonnull SubmitStatementRequest submitStatementRequest, @javax.annotation.Nullable UUID requestId, @javax.annotation.Nullable Boolean async, @javax.annotation.Nullable Boolean nullable, @javax.annotation.Nullable String accept, @javax.annotation.Nullable String xSnowflakeAuthorizationTokenType) throws WebClientResponseException {
        Object postBody = submitStatementRequest;
        // verify the required parameter 'userAgent' is set
        if (userAgent == null) {
            throw new WebClientResponseException("Missing the required parameter 'userAgent' when calling submitStatement", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'submitStatementRequest' is set
        if (submitStatementRequest == null) {
            throw new WebClientResponseException("Missing the required parameter 'submitStatementRequest' when calling submitStatement", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "requestId", requestId));
        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "async", async));
        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "nullable", nullable));

        if (accept != null)
        headerParams.add("Accept", apiClient.parameterToString(accept));
        if (userAgent != null)
        headerParams.add("User-Agent", apiClient.parameterToString(userAgent));
        if (xSnowflakeAuthorizationTokenType != null)
        headerParams.add("X-Snowflake-Authorization-Token-Type", apiClient.parameterToString(xSnowflakeAuthorizationTokenType));
        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { 
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] { "ExternalOAuth", "KeyPair", "SnowflakeOAuth" };

        ParameterizedTypeReference<ResultSet> localVarReturnType = new ParameterizedTypeReference<ResultSet>() {};
        return apiClient.invokeAPI("/api/v2/statements", HttpMethod.POST, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Submits a SQL statement for execution.
     * Submits one or more statements for execution. You can specify that the statement should be executed asynchronously.
     * <p><b>200</b> - The statement was executed successfully, and the response includes any data requested.
     * <p><b>202</b> - The execution of the statement is still in progress. Use GET /statements/ and specify the statement handle to check the status of the statement execution.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This happens if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint is wrong. For example, if the application hits /api/api/hello which doesn&#39;t exist, it will receive this code.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST. The application must change a method for retry.
     * <p><b>408</b> - The execution of the statement exceeded the timeout period. The execution of the statement was cancelled.
     * <p><b>415</b> - The request header Content-Type includes unsupported media type. The API supports application/json only. If none specified, the request payload is taken as JSON, but if any other media type is specified, this error is returned.
     * <p><b>422</b> - An error occurred when executing the statement. Check the error code and error message for details.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hits an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended. https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended. https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     * @param userAgent Set this to the name and version of your application (e.g. “applicationName/applicationVersion”). You must use a value that complies with RFC 7231.
     * @param submitStatementRequest Specifies the SQL statement to execute and the statement context.
     * @param requestId Unique ID of the API request. This ensures that the execution is idempotent. If not specified, a new UUID is generated and assigned.
     * @param async Set to true to execute the statement asynchronously and return the statement handle. If the parameter is not specified or is set to false, a statement is executed and the first result is returned if the execution is completed in 45 seconds. If the statement execution takes longer to complete, the statement handle is returned.
     * @param nullable Set to true to execute the statement to generate the result set including null. If the parameter is set to false, the result set value null will be replaced with a string &#39;null&#39;.
     * @param accept The response payload format. The schema should be specified in resultSetMetaData in the request payload.
     * @param xSnowflakeAuthorizationTokenType Specify the authorization token type for the Authorization header. KEYPAIR_JWT is for Keypair JWT or OAUTH for oAuth token. If not specified, OAUTH is assumed.
     * @return ResultSet
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResultSet> submitStatement(@javax.annotation.Nonnull String userAgent, @javax.annotation.Nonnull SubmitStatementRequest submitStatementRequest, @javax.annotation.Nullable UUID requestId, @javax.annotation.Nullable Boolean async, @javax.annotation.Nullable Boolean nullable, @javax.annotation.Nullable String accept, @javax.annotation.Nullable String xSnowflakeAuthorizationTokenType) throws WebClientResponseException {
        ParameterizedTypeReference<ResultSet> localVarReturnType = new ParameterizedTypeReference<ResultSet>() {};
        return submitStatementRequestCreation(userAgent, submitStatementRequest, requestId, async, nullable, accept, xSnowflakeAuthorizationTokenType).bodyToMono(localVarReturnType);
    }

    /**
     * Submits a SQL statement for execution.
     * Submits one or more statements for execution. You can specify that the statement should be executed asynchronously.
     * <p><b>200</b> - The statement was executed successfully, and the response includes any data requested.
     * <p><b>202</b> - The execution of the statement is still in progress. Use GET /statements/ and specify the statement handle to check the status of the statement execution.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This happens if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint is wrong. For example, if the application hits /api/api/hello which doesn&#39;t exist, it will receive this code.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST. The application must change a method for retry.
     * <p><b>408</b> - The execution of the statement exceeded the timeout period. The execution of the statement was cancelled.
     * <p><b>415</b> - The request header Content-Type includes unsupported media type. The API supports application/json only. If none specified, the request payload is taken as JSON, but if any other media type is specified, this error is returned.
     * <p><b>422</b> - An error occurred when executing the statement. Check the error code and error message for details.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hits an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended. https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended. https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     * @param userAgent Set this to the name and version of your application (e.g. “applicationName/applicationVersion”). You must use a value that complies with RFC 7231.
     * @param submitStatementRequest Specifies the SQL statement to execute and the statement context.
     * @param requestId Unique ID of the API request. This ensures that the execution is idempotent. If not specified, a new UUID is generated and assigned.
     * @param async Set to true to execute the statement asynchronously and return the statement handle. If the parameter is not specified or is set to false, a statement is executed and the first result is returned if the execution is completed in 45 seconds. If the statement execution takes longer to complete, the statement handle is returned.
     * @param nullable Set to true to execute the statement to generate the result set including null. If the parameter is set to false, the result set value null will be replaced with a string &#39;null&#39;.
     * @param accept The response payload format. The schema should be specified in resultSetMetaData in the request payload.
     * @param xSnowflakeAuthorizationTokenType Specify the authorization token type for the Authorization header. KEYPAIR_JWT is for Keypair JWT or OAUTH for oAuth token. If not specified, OAUTH is assumed.
     * @return ResponseEntity&lt;ResultSet&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<ResultSet>> submitStatementWithHttpInfo(@javax.annotation.Nonnull String userAgent, @javax.annotation.Nonnull SubmitStatementRequest submitStatementRequest, @javax.annotation.Nullable UUID requestId, @javax.annotation.Nullable Boolean async, @javax.annotation.Nullable Boolean nullable, @javax.annotation.Nullable String accept, @javax.annotation.Nullable String xSnowflakeAuthorizationTokenType) throws WebClientResponseException {
        ParameterizedTypeReference<ResultSet> localVarReturnType = new ParameterizedTypeReference<ResultSet>() {};
        return submitStatementRequestCreation(userAgent, submitStatementRequest, requestId, async, nullable, accept, xSnowflakeAuthorizationTokenType).toEntity(localVarReturnType);
    }

    /**
     * Submits a SQL statement for execution.
     * Submits one or more statements for execution. You can specify that the statement should be executed asynchronously.
     * <p><b>200</b> - The statement was executed successfully, and the response includes any data requested.
     * <p><b>202</b> - The execution of the statement is still in progress. Use GET /statements/ and specify the statement handle to check the status of the statement execution.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This happens if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint is wrong. For example, if the application hits /api/api/hello which doesn&#39;t exist, it will receive this code.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST. The application must change a method for retry.
     * <p><b>408</b> - The execution of the statement exceeded the timeout period. The execution of the statement was cancelled.
     * <p><b>415</b> - The request header Content-Type includes unsupported media type. The API supports application/json only. If none specified, the request payload is taken as JSON, but if any other media type is specified, this error is returned.
     * <p><b>422</b> - An error occurred when executing the statement. Check the error code and error message for details.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hits an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended. https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended. https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
     * @param userAgent Set this to the name and version of your application (e.g. “applicationName/applicationVersion”). You must use a value that complies with RFC 7231.
     * @param submitStatementRequest Specifies the SQL statement to execute and the statement context.
     * @param requestId Unique ID of the API request. This ensures that the execution is idempotent. If not specified, a new UUID is generated and assigned.
     * @param async Set to true to execute the statement asynchronously and return the statement handle. If the parameter is not specified or is set to false, a statement is executed and the first result is returned if the execution is completed in 45 seconds. If the statement execution takes longer to complete, the statement handle is returned.
     * @param nullable Set to true to execute the statement to generate the result set including null. If the parameter is set to false, the result set value null will be replaced with a string &#39;null&#39;.
     * @param accept The response payload format. The schema should be specified in resultSetMetaData in the request payload.
     * @param xSnowflakeAuthorizationTokenType Specify the authorization token type for the Authorization header. KEYPAIR_JWT is for Keypair JWT or OAUTH for oAuth token. If not specified, OAUTH is assumed.
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec submitStatementWithResponseSpec(@javax.annotation.Nonnull String userAgent, @javax.annotation.Nonnull SubmitStatementRequest submitStatementRequest, @javax.annotation.Nullable UUID requestId, @javax.annotation.Nullable Boolean async, @javax.annotation.Nullable Boolean nullable, @javax.annotation.Nullable String accept, @javax.annotation.Nullable String xSnowflakeAuthorizationTokenType) throws WebClientResponseException {
        return submitStatementRequestCreation(userAgent, submitStatementRequest, requestId, async, nullable, accept, xSnowflakeAuthorizationTokenType);
    }
}

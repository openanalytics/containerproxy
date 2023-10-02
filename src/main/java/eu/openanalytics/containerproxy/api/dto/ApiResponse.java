/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2023 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.containerproxy.api.dto;

import com.fasterxml.jackson.annotation.JsonView;
import eu.openanalytics.containerproxy.model.Views;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJacksonValue;

public class ApiResponse<T> {

    private final String status;
    private final Object data;

    public ApiResponse(String status, Object data) {
        this.status = status;
        this.data = data;
    }

    public static <T> ResponseEntity<ApiResponse<T>> success(T data) {
        return ResponseEntity.ok(new ApiResponse<>("success", data));
    }

    public static <T> ResponseEntity<ApiResponse<T>> created(T data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>("success", data));
    }

    public static ResponseEntity<MappingJacksonValue> success(MappingJacksonValue data) {
        data.setValue(new ApiResponse<>("success", data.getValue()));
        return ResponseEntity.ok(data);
    }

    public static <T> ResponseEntity<ApiResponse<T>> success() {
        return ResponseEntity.ok(new ApiResponse<>("success", null));
    }

    // forbidden
    public static <T> ResponseEntity<ApiResponse<T>> failForbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>("fail", "forbidden"));
    }

    // unauthorized
    public static <T> ResponseEntity<ApiResponse<T>> failUnauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>("error", "unauthorized"));
    }

    public static <T> ResponseEntity<ApiResponse<T>> failNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>("error", "not found"));
    }

    // invalid request
    public static <T> ResponseEntity<ApiResponse<T>> fail(Object data) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>("fail", data));
    }

    // internal error
    public static <T> ResponseEntity<ApiResponse<T>> error(Object data) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>("error", data));
    }

    public static <T> ApiResponse<T> errorBody(Object data) {
        return new ApiResponse<>("error", data);
    }


    @JsonView(Views.Default.class)
    public String getStatus() {
        return status;
    }

    @JsonView(Views.Default.class)
    public Object getData() {
        return data;
    }

}

package com.hospital.converter;

import com.hospital.dto.response.LoginResponse;
import com.hospital.dto.response.UserInfoResponse;
import com.hospital.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DtoMapper {

    DtoMapper INSTANCE = Mappers.getMapper(DtoMapper.class);

    UserInfoResponse toUserInfoResponse(User user);

    @Mapping(target = "token", ignore = true)
    LoginResponse toLoginResponse(User user);

    List<UserInfoResponse> toUserInfoResponseList(List<User> users);
}


















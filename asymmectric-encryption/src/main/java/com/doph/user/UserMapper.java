package com.doph.user;

import ch.qos.logback.core.util.StringUtil;
import com.doph.user.request.ProfileUpdateRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class UserMapper {

    public void mergerUserInfo(final User user, final ProfileUpdateRequest request){
        if(StringUtils.isNotBlank(request.getFirstName())
                && !user.getFirstName().equals(request.getFirstName())
        ){
            user.setFirstName(request.getFirstName());
        }
        if(StringUtils.isNotBlank(request.getLastName())
                && !user.getLastName().equals(request.getLastName())
        ){
            user.setLastName(request.getLastName());
        }
    }

}

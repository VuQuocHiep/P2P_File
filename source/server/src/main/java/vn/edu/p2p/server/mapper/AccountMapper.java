package vn.edu.p2p.server.mapper;

import org.springframework.stereotype.Component;
import vn.edu.p2p.server.dto.response.UserResponse;
import vn.edu.p2p.server.entity.AccountEntity;

@Component
public class AccountMapper {
    public UserResponse toResponse(AccountEntity account) {
        return new UserResponse(account.username, account.role, account.status, account.created);
    }
}

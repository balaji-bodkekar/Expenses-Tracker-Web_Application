package com.SpringBootMVC.ExpensesTracker.DTO;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class CustomUserDetails extends User {

  private int clientId;

  public CustomUserDetails(
      String username,
      String password,
      Collection<? extends GrantedAuthority> authorities,
      int clientId) {
    super(username, password, authorities);
    this.clientId = clientId;
  }

  public int getClientId() {
    return clientId;
  }
}

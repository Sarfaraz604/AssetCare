package com.assetcare.config;

import com.assetcare.model.*;
import com.assetcare.repository.*;
import java.time.Clock;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.seed-enabled", havingValue = "true")
public class DemoSeed implements CommandLineRunner {
  private final UserRepository users;
  private final AssetRepository assets;
  private final PasswordEncoder encoder;
  private final Environment environment;
  private final Clock clock;

  public DemoSeed(
      UserRepository users,
      AssetRepository assets,
      PasswordEncoder encoder,
      Environment environment,
      Clock clock) {
    this.users = users;
    this.assets = assets;
    this.encoder = encoder;
    this.environment = environment;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void run(String... args) {
    createUser("admin", "Alex Morgan", Role.ADMIN, "DEMO_ADMIN_PASSWORD");
    createUser("technician", "Sam Rivera", Role.TECHNICIAN, "DEMO_TECHNICIAN_PASSWORD");
    createUser("viewer", "Jordan Lee", Role.VIEWER, "DEMO_VIEWER_PASSWORD");
    if (assets.count() == 0) {
      asset("HVAC-001", "North wing air handler", "HVAC", "Building A / Roof");
      asset("PUMP-002", "Chilled water pump", "Mechanical", "Building A / Plant room");
      asset("ELEC-003", "Main distribution panel", "Electrical", "Building B / Ground floor");
    }
  }

  private void createUser(String username, String name, Role role, String variable) {
    if (users.findByUsername(username).isPresent()) return;
    String password = environment.getProperty(variable);
    if (password == null
        || password.length() < 12
        || password.length() > 72
        || password.startsWith("REPLACE_"))
      throw new IllegalStateException(
          variable + " must contain a local demo password of 12-72 characters.");
    AppUser user = new AppUser();
    user.setUsername(username);
    user.setDisplayName(name);
    user.setRole(role);
    user.setPasswordHash(encoder.encode(password));
    users.save(user);
  }

  private void asset(String code, String name, String category, String location) {
    Asset asset = new Asset();
    asset.setCode(code);
    asset.setName(name);
    asset.setCategory(category);
    asset.setLocation(location);
    asset.setStatus(AssetStatus.ACTIVE);
    asset.setCreatedAt(clock.instant());
    asset.setUpdatedAt(clock.instant());
    assets.save(asset);
  }
}

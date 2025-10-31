plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.unisync"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

extra["springCloudVersion"] = "2024.0.0"
extra["awsSdkVersion"] = "2.29.45"

dependencies {
    // Spring Cloud Gateway
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")

    // Spring Boot Actuator (헬스체크)
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Spring Boot Validation (ConfigurationProperties 검증)
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // AWS SDK for Cognito (Public Key 가져오기)
    implementation("software.amazon.awssdk:cognitoidentityprovider:${property("awsSdkVersion")}")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // JSON Web Token (JWT) - Cognito JWT 검증
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Test Dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.cloud:spring-cloud-starter-contract-stub-runner")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

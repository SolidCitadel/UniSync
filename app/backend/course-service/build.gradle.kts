plugins {
    java
    id("org.springframework.boot") version "3.5.7"
    id("io.spring.dependency-management") version "1.1.7"
    id("co.uzzu.dotenv.gradle") version "4.0.0"
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

extra["awsSdkVersion"] = "2.29.45"
extra["springCloudAwsVersion"] = "3.2.1"

dependencies {
    // Shared Common Module
    implementation("com.unisync:java-common:1.0.0")

    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // MySQL Driver
    runtimeOnly("com.mysql:mysql-connector-j")

    // AWS SDK for SQS
    implementation("software.amazon.awssdk:sqs:${property("awsSdkVersion")}")

    // Spring Cloud AWS for SQS Listener
    implementation("io.awspring.cloud:spring-cloud-aws-starter-sqs:${property("springCloudAwsVersion")}")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // SpringDoc OpenAPI (Swagger)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.4")

    // JSON Web Token (JWT) - for authentication validation
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Test Dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers:1.19.0")
    testImplementation("org.testcontainers:junit-jupiter:1.19.0")
    testImplementation("org.testcontainers:mysql:1.19.0")
    testImplementation("org.testcontainers:localstack:1.19.0")
    testImplementation("org.awaitility:awaitility:4.2.0")
}

tasks.withType<Test> {
    useJUnitPlatform()

    // .env.local의 환경변수 주입 (dotenv 플러그인 사용)
    environment(env.allVariables())
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    // .env.local의 환경변수 주입 (dotenv 플러그인 사용)
    environment(env.allVariables())
}
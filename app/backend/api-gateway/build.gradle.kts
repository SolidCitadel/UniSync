import io.github.cdimascio.dotenv.Dotenv

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("io.github.cdimascio:java-dotenv:5.2.2")
    }
}

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

    // SpringDoc OpenAPI - Swagger Aggregation (WebFlux 기반)
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.4")

    // Test Dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.cloud:spring-cloud-starter-contract-stub-runner")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    // Testcontainers for LocalStack
    testImplementation("org.testcontainers:testcontainers:1.20.4")
    testImplementation("org.testcontainers:localstack:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}

// 루트 디렉토리 (api-gateway -> backend -> app -> UniSync)
val rootDir = projectDir.parentFile.parentFile.parentFile

// .env.local 로드 (모든 설정 포함: 비밀 + 로컬 전용 + 공통)
val localEnv = Dotenv.configure()
    .directory(rootDir.absolutePath)
    .filename(".env.local")
    .ignoreIfMissing()
    .load()

val envMap = localEnv.entries().associate { it.key to it.value }

tasks.withType<Test> {
    useJUnitPlatform()

    // .env.local 환경변수 주입
    environment(envMap)
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    // .env.local 환경변수 주입
    environment(envMap)
}

// 환경변수 확인용 태스크
tasks.register("printEnv") {
    doLast {
        println("=== Loaded Environment Variables ===")
        println("Total variables loaded: ${envMap.size}")
        println("\nFrom .env.local:")
        println("  - USER_SERVICE_URL: ${envMap["USER_SERVICE_URL"] ?: "NOT SET"}")
        println("  - COURSE_SERVICE_URL: ${envMap["COURSE_SERVICE_URL"] ?: "NOT SET"}")
        println("  - SCHEDULE_SERVICE_URL: ${envMap["SCHEDULE_SERVICE_URL"] ?: "NOT SET"}")
        println("  - COGNITO_REGION: ${envMap["COGNITO_REGION"] ?: "NOT SET"}")
        println("  - JWT_EXPIRATION: ${envMap["JWT_EXPIRATION"] ?: "NOT SET"}")
        println("\nSecrets (masked):")
        println("  - JWT_SECRET: ${if (envMap["JWT_SECRET"] != null) "***SET***" else "NOT SET"}")
        println("  - COGNITO_USER_POOL_ID: ${envMap["COGNITO_USER_POOL_ID"] ?: "NOT SET"}")
        println("  - COGNITO_CLIENT_ID: ${envMap["COGNITO_CLIENT_ID"] ?: "NOT SET"}")
    }
}

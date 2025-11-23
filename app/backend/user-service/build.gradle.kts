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
    id("org.springframework.boot") version "3.5.7"
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

extra["awsSdkVersion"] = "2.29.45"

dependencies {
    // Shared Modules
    implementation("com.unisync:java-common:1.0.0")

    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // MySQL Driver
    runtimeOnly("com.mysql:mysql-connector-j")

    // AWS SDK for Cognito
    implementation("software.amazon.awssdk:cognitoidentityprovider:${property("awsSdkVersion")}")

    // AWS SDK for SQS
    implementation("software.amazon.awssdk:sqs:${property("awsSdkVersion")}")

    // AWS SDK for Lambda
    implementation("software.amazon.awssdk:lambda:${property("awsSdkVersion")}")
    implementation("software.amazon.awssdk:apache-client:${property("awsSdkVersion")}")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // SpringDoc OpenAPI (Swagger)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.4")

    // JSON Web Token (JWT)
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Test Dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("com.h2database:h2")
}

// 루트 디렉토리 (user-service -> backend -> app -> UniSync)
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
        println("  - SQS_USER_TOKEN_REGISTERED_QUEUE: ${envMap["SQS_USER_TOKEN_REGISTERED_QUEUE"] ?: "NOT SET"}")
        println("  - CANVAS_API_BASE_URL: ${envMap["CANVAS_API_BASE_URL"] ?: "NOT SET"}")
        println("  - USER_SERVICE_DATABASE_URL: ${envMap["USER_SERVICE_DATABASE_URL"] ?: "NOT SET"}")
        println("\nSecrets (masked):")
        println("  - ENCRYPTION_KEY: ${if (envMap["ENCRYPTION_KEY"] != null) "***SET***" else "NOT SET"}")
        println("  - JWT_SECRET: ${if (envMap["JWT_SECRET"] != null) "***SET***" else "NOT SET"}")
    }
}

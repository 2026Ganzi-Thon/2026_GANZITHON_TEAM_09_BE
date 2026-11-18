plugins {
    java
    jacoco
    alias(libs.plugins.spring.boot)
}

group = "com.haengunping"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// SEC-14: 해석된 의존성 버전을 gradle.lockfile에 고정한다.
// 의존성을 바꾸면 ./gradlew dependencies --write-locks 로 잠금 파일을 갱신해 같은 PR에 올린다.
dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.archunit.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // 생성자 파라미터 이름을 남겨 @RequestParam·@PathVariable 이름 생략을 허용한다.
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
    inputs.file("deploy/seed-demo-online.sql")
        .withPropertyName("demoOnlineSeed")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    inputs.file("deploy/verify-demo-online.sql")
        .withPropertyName("demoOnlineVerify")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

// 규칙 계산이 모이는 domain 패키지는 줄 커버리지 80% 이상(docs/architecture.md 테스트 표).
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            element = "PACKAGE"
            includes = listOf("com.haengunping.*.domain*")
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

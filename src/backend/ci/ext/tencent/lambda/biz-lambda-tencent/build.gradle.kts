/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

dependencies {
    api(project(":core:common:common-service"))
    api(project(":core:common:common-web"))
    api(project(":ext:tencent:common:common-digest-tencent"))
    api(project(":ext:tencent:common:common-kafka-tencent"))
    api(project(":core:common:common-event"))
    api(project(":core:common:common-client"))
    api(project(":core:process:api-process"))
    api(project(":ext:tencent:lambda:model-lambda"))
    api(project(":core:project:api-project"))
    api(project(":ext:tencent:lambda:api-lambda-tencent"))
    testImplementation(project(":core:common:common-test"))
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-xml")
    api("org.elasticsearch:elasticsearch")
    api("org.elasticsearch.client:transport")
    api("org.elasticsearch.plugin:transport-netty4-client")
    api("com.floragunn:search-guard-ssl")
    api("org.json:json")
    api("org.springframework.boot:spring-boot-starter-jooq")
    api("com.zaxxer:HikariCP")
    api("org.jooq:jooq")
    api("mysql:mysql-connector-java")
}
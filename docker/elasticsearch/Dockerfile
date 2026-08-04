# 공식 Elasticsearch Image는 Nori(한국어 형태소) Plugin을 기본 포함하지 않는다. Client
# Library(co.elastic.clients:elasticsearch-java) Version과 맞춘 고정 Patch Tag 위에 공식
# Plugin만 설치한다(Issue #69). Elasticsearch Version은 build.gradle.kts가 resolve하는
# co.elastic.clients:elasticsearch-java:9.4.2와 맞춘다.
FROM docker.elastic.co/elasticsearch/elasticsearch:9.4.2

RUN elasticsearch-plugin install --batch analysis-nori

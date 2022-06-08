# Image provides a container in which to run OpenOffice transformations for Alfresco Content Services.

# OpenOffice is from Apache Foundation. See the license at https://www.openoffice.org/license.html .

FROM alfresco/alfresco-base-java:jre11-centos7-202204121522

ARG LIBREOFFICE_VERSION=4.1.12

ENV LIBREOFFICE_RPM_URL=https://sourceforge.net/projects/openofficeorg.mirror/files/4.1.12/binaries/es/Apache_OpenOffice_4.1.12_Linux_x86-64_install-rpm_es.tar.gz
ENV JAVA_OPTS=""

# Set default user information
ARG GROUPNAME=Alfresco
ARG GROUPID=1000
ARG LIBREUSERNAME=libreoffice
ARG USERID=33003

COPY target/${env.project_artifactId}-${env.project_version}.jar libreoffice-dist-*-linux.gz /

RUN ln /${env.project_artifactId}-${env.project_version}.jar /usr/bin/${env.project_artifactId}.jar && \
    yum install -y cairo cups-libs libSM libGLU && \
    test -f libreoffice-dist-${LIBREOFFICE_VERSION}-linux.gz && \
    ln -s libreoffice-dist-${LIBREOFFICE_VERSION}-linux.gz libreoffice-dist-linux.gz || \
    curl -L -s -S $LIBREOFFICE_RPM_URL      -o libreoffice-dist-linux.gz && \
    tar xzf libreoffice-dist-linux.gz && \
    yum localinstall -y es/RPMS/*.rpm && \
    rm -rf libreoffice-dist-*linux.gz es && \
    yum clean all

ADD target/generated-resources/licenses              /licenses
ADD target/generated-resources/licenses.xml          /licenses/
ADD target/generated-sources/license/THIRD-PARTY.txt /licenses/
COPY src/main/resources/licenses/3rd-party/ /

RUN groupadd -g ${GROUPID} ${GROUPNAME} && \
    useradd -u ${USERID} -G ${GROUPNAME} ${LIBREUSERNAME} && \
    chgrp -R ${GROUPNAME} /usr/bin/${env.project_artifactId}.jar

EXPOSE 8090

USER ${LIBREUSERNAME}

ENTRYPOINT java $JAVA_OPTS -jar /usr/bin/${env.project_artifactId}.jar

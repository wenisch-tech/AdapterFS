{{- define "adapterfs.name" -}}adapterfs{{- end }}
{{- define "adapterfs.fullname" -}}{{ printf "%s-adapterfs" .Release.Name | trunc 63 | trimSuffix "-" }}{{- end }}
{{- define "adapterfs.labels" -}}
app.kubernetes.io/name: {{ include "adapterfs.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}
{{- define "adapterfs.selectorLabels" -}}
app.kubernetes.io/name: {{ include "adapterfs.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
{{- define "adapterfs.secretName" -}}{{ default (include "adapterfs.fullname" .) .Values.credentials.existingSecret }}{{- end }}

{{- define "adapterfs.container" -}}
- name: adapterfs
  image: "{{ .Values.image.repository }}:{{ default .Chart.AppVersion .Values.image.tag }}"
  imagePullPolicy: {{ .Values.image.pullPolicy }}
  securityContext: {{ toYaml .Values.securityContext | nindent 4 }}
  ports:
    - {name: http, containerPort: {{ .Values.protocols.http.port }}}
    {{- if .Values.protocols.s3.enabled }}
    - {name: s3, containerPort: {{ .Values.protocols.s3.port }}}
    {{- end }}
    {{- if .Values.protocols.sftp.enabled }}
    - {name: sftp, containerPort: {{ .Values.protocols.sftp.port }}}
    {{- end }}
    {{- if .Values.protocols.ftp.enabled }}
    - {name: ftp, containerPort: {{ .Values.protocols.ftp.port }}}
    {{- end }}
  env:
    - {name: ADAPTERFS_HTTP_PORT, value: {{ .Values.protocols.http.port | quote }}}
    - {name: ADAPTERFS_S3_ENABLED, value: {{ .Values.protocols.s3.enabled | quote }}}
    - {name: ADAPTERFS_S3_PORT, value: {{ .Values.protocols.s3.port | quote }}}
    - {name: ADAPTERFS_SFTP_ENABLED, value: {{ .Values.protocols.sftp.enabled | quote }}}
    - {name: ADAPTERFS_SFTP_PORT, value: {{ .Values.protocols.sftp.port | quote }}}
    - {name: ADAPTERFS_FTP_ENABLED, value: {{ .Values.protocols.ftp.enabled | quote }}}
    - {name: ADAPTERFS_FTP_PORT, value: {{ .Values.protocols.ftp.port | quote }}}
    - {name: ADAPTERFS_FTP_PASSIVE_PORTS, value: {{ .Values.protocols.ftp.passivePorts | quote }}}
    - {name: ADAPTERFS_FTP_EXTERNAL_ADDRESS, value: {{ .Values.protocols.ftp.externalAddress | quote }}}
    - {name: SPRING_CONFIG_ADDITIONAL_LOCATION, value: file:/config/application.yaml}
    - name: ADAPTERFS_AUTH_USERNAME
      valueFrom: {secretKeyRef: {name: {{ include "adapterfs.secretName" . }}, key: username}}
    - name: ADAPTERFS_AUTH_PASSWORD
      valueFrom: {secretKeyRef: {name: {{ include "adapterfs.secretName" . }}, key: password}}
    - name: ADAPTERFS_AUTH_S3_ACCESS_KEY
      valueFrom: {secretKeyRef: {name: {{ include "adapterfs.secretName" . }}, key: s3-access-key}}
    - name: ADAPTERFS_AUTH_S3_SECRET_KEY
      valueFrom: {secretKeyRef: {name: {{ include "adapterfs.secretName" . }}, key: s3-secret-key}}
    {{- with .Values.extraEnv }}
    {{- toYaml . | nindent 4 }}
    {{- end }}
  volumeMounts:
    - {name: adapterfs-state, mountPath: /var/lib/adapterfs}
    - {name: adapterfs-config, mountPath: /config, readOnly: true}
    {{- with .Values.extraVolumeMounts }}
    {{- toYaml . | nindent 4 }}
    {{- end }}
  readinessProbe: {httpGet: {path: /actuator/health/readiness, port: http}, initialDelaySeconds: 5, periodSeconds: 10}
  livenessProbe: {httpGet: {path: /actuator/health/liveness, port: http}, initialDelaySeconds: 20, periodSeconds: 20}
  resources: {{ toYaml .Values.resources | nindent 4 }}
{{- end }}

{{/*
Etiquetas comunes, reutilizadas en cada Deployment y Service del chart.
*/}}
{{- define "solicitudes-gov.labels" -}}
app.kubernetes.io/part-of: solicitudes-gov
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end -}}

{{/*
Nombre de imagen completo: registro/repositorio:etiqueta.
Uso: {{ include "solicitudes-gov.imagen" (dict "Values" .Values "imagen" .Values.solicitudesService.imagen) }}
*/}}
{{- define "solicitudes-gov.imagen" -}}
{{ .Values.imagen.registro }}/{{ .imagen }}:{{ .Values.imagen.etiqueta }}
{{- end -}}

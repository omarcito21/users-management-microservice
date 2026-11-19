# Users Management — Spring Boot, Arquitectura Hexagonal y DDD

Aplicación de gestión de usuarios construida con Java 17 y Spring Boot. La API REST es el punto de entrada activo. El código de la antigua CLI se conserva como adaptador inactivo y no posee un contenedor de dependencias independiente.

Spring es el único *composition root*: `Main` inicia el contexto y las dependencias se resuelven mediante configuración y component scanning de Spring.

## Verificación

`GET /health` es publico y responde HTTP 200 con los metadatos generados desde
`pom.xml` durante el build:

```json
{"status":"UP","name":"users-management","version":"2.2.0"}
```

Indica que la API responde; no comprueba MySQL ni el proveedor de correo.

```bash
./mvnw clean test
./mvnw clean package
```

En Windows se puede utilizar `mvnw.cmd`.

## Ejecución local con MySQL o TiDB

Para levantar el entorno reproducible incluido en el repositorio, copie la plantilla y reemplace los valores ficticios por credenciales propias:

```bash
cp .env.example .env
docker compose up --build
```

La plantilla `.env.example` no contiene credenciales válidas y sí está versionada; el archivo `.env` real queda ignorado por Git y Docker. Compose inicializa el esquema desde `src/main/resources/schema.sql`. En desarrollo, la base local usa MySQL; para conectar directamente a TiDB Cloud, configure `DB_HOST`, `DB_PORT=4000`, `DB_NAME=crud_usuarios`, `DB_USERNAME`, `DB_PASSWORD` y `DB_SSL_MODE=VERIFY_IDENTITY`, y ejecute la API con esas variables. El conector JDBC de MySQL es compatible con TiDB.

## Notificaciones por correo con Brevo

La API de Brevo se habilita con `NOTIFICATION_PROVIDER=brevo`, una clave REST que comience por `xkeysib-` y una dirección remitente verificada en Brevo. Configure `BREVO_API_KEY`, `BREVO_FROM_ADDRESS` y `BREVO_FROM_NAME`; no use la clave SMTP como API key. El cliente REST usa timeouts, reintenta fallos transitorios de red/HTTP 5xx con demora exponencial y abre un Circuit Breaker ante fallos consecutivos. Las respuestas 4xx no se reintentan. Si desea conservar SMTP, establezca `NOTIFICATION_PROVIDER=smtp` y configure `SMTP_*`.

## Integración continua y Render

`.github/workflows/ci.yml` ejecuta `./mvnw clean verify` con Java 17 ante cambios a `main` y pull requests. El archivo `render.yaml` define el despliegue Docker con auto-deploy, `/health` como health check, TLS para MySQL/TiDB y Brevo como proveedor de email. Al crear el Blueprint, complete de forma privada las variables `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD`, `BREVO_API_KEY` y `BREVO_FROM_ADDRESS`. En TiDB use puerto `4000` y confirme que la instancia y remitente de Brevo estén activos/verificados. No se deben guardar credenciales en el repositorio. El despliegue remoto requiere configurar las cuentas y secretos en los paneles respectivos; este repositorio no incluye esas credenciales.

## Despliegue en Render

El archivo `render.yaml` define el servicio web, el build con Docker y el despliegue
automático de cada commit que llegue a la rama `main`.

1. Crear un Blueprint en Render y seleccionar este repositorio.
2. Completar los secretos marcados como requeridos: `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD`, `BREVO_API_KEY` y `BREVO_FROM_ADDRESS`.
3. Usar una instancia MySQL/TiDB accesible por Render y ejecutar `src/main/resources/schema.sql` sobre la base de datos.
4. Desplegar el Blueprint. La API quedará disponible en el subdominio `onrender.com` asignado por Render, Swagger UI en `/swagger-ui/index.html` y el health check en `/health`.

Las credenciales nunca deben guardarse en `application.properties` ni en
`render.yaml`. Para desarrollo local, deben proporcionarse como variables de entorno.

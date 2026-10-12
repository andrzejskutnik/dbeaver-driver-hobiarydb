# HobiaryDB JDBC / DBeaver driver

An experimental Java 25 JDBC driver for the custom
[HobiaryDB C11 engine](https://github.com/andrzejskutnik/hobiarydb).
It maps JDBC connections, statements, result sets and metadata onto the
engine's binary TCP protocol. It is a portfolio preview, not a fully
JDBC-compliant or production-certified driver.

## Run the demo without a database server

Requirements: JDK 25 and Maven 3.9+. Check `java -version` and `mvn -version`;
both must use JDK 25.

```sh
mvn verify
```

This builds the JAR and runs the tests, including a **self-contained loopback
protocol demo**. The fixture binds only to `127.0.0.1` on a random port,
returns two deterministic rows and one server error, and then shuts down.
No credentials, native library, broker or external service are needed.

To run the demo separately after building:

```powershell
# Windows
java -cp "target\test-classes;target\classes" pl.hobiarydb.jdbc.JdbcDemo
```

```sh
# Linux/macOS
java -cp "target/test-classes:target/classes" pl.hobiarydb.jdbc.JdbcDemo
```

Expected output (the port varies):

```text
Loopback protocol fixture (no native database): jdbc:hobiarydb://127.0.0.1:...
42 | Ada | NULL
43 | Grace | compiler
Server error propagated as SQLException.
PASS: JDBC auto-loading, TCP framing, metadata, rows, NULL and server error verified.
```

**Scope:** this verifies the real driver over a real TCP socket, including
automatic JDBC registration, framing, metadata, SQL NULL and error propagation.
The fixture does not execute SQL or store data. It is not an end-to-end test of
the C engine, TLS authentication or the DBeaver GUI.

## Use with a real HobiaryDB server

Build and start the TCP server from the
[engine repository](https://github.com/andrzejskutnik/hobiarydb); that server
currently requires its Linux/POSIX build environment. The default port is 5566.

```java
import java.sql.DriverManager;
import java.util.Properties;

var properties = new Properties();
properties.setProperty("user", System.getenv("HOBIARY_USER"));
properties.setProperty("password", System.getenv("HOBIARY_PASSWORD"));
try (var connection = DriverManager.getConnection(
        "jdbc:hobiarydb://localhost:5566", properties);
     var statement = connection.createStatement();
     var rows = statement.executeQuery("SELECT * FROM demo_people")) {
    while (rows.next()) {
        System.out.println(rows.getString(1));
    }
}
```

Create `demo_people` and its schema on the server first; DML requires a schema.
Set the two environment variables before using the authenticated example.

A nonempty password automatically enables TLS; authenticated connections must
use a TLS-enabled server. `ssl=true` also enables TLS in open mode. Certificates
must chain to the JVM trust store and match the hostname. For a local/private CA,
configure the JVM trust store rather than disabling certificate validation.
Open, unencrypted mode is for isolated local experiments only.

## Configure DBeaver

1. Run `mvn package`. The driver JAR is `target/hobiarydb-jdbc-0.2.0.jar`.
2. Use a DBeaver installation running on **Java 25 or newer**. An older bundled
   JVM cannot load this JAR.
3. Open **Database > Driver Manager > New** and name it `HobiaryDB`.
4. Under **Libraries**, add the JAR.
5. Set driver class to `pl.hobiarydb.jdbc.HobiaryDriver`.
6. Set URL template to `jdbc:hobiarydb://{host}:{port}`, default port `5566`.
7. Create a connection to your running server. Configure username/password and
   trust store as described above.

This repository contains a JDBC JAR and setup instructions, not a packaged
DBeaver extension. A live DBeaver session is not part of the loopback demo.

## Implementation boundaries

- Binary, big-endian protocol; one in-flight request per connection.
- Result data is buffered in memory; values arrive as text.
- `jdbcCompliant()` returns `false`.
- Transaction calls currently do not implement server transactions; do not
  rely on `commit`, `rollback` or an auto-commit flag for atomicity.
- Query timeouts and maximum-row settings are not currently enforced.
- Metadata support is partial; not all DBeaver operations are supported.

## Portfolio ecosystem

- [HobiaryDB](https://github.com/andrzejskutnik/hobiarydb) — custom embedded C11 database and SQL engine.
- [HobiaryLogDB](https://github.com/andrzejskutnik/hobiary-logdb) — append-only native log storage with reopen/readback demo.
- [HobiaryLogPipe](https://github.com/andrzejskutnik/hobiary-logpipe) — .NET logging pipeline with brokerless console and browser demos.

Licensed under [Apache-2.0](LICENSE).

# Guía de Pruebas Unitarias Automatizadas

**Para programadores que nunca hicieron testing automatizado**

---

## Tabla de contenidos

1. [El problema que resuelven los tests](#1-el-problema-que-resuelven-los-tests)
2. [¿Qué es una prueba unitaria?](#2-qué-es-una-prueba-unitaria)
3. [La pirámide de testing](#3-la-pirámide-de-testing)
4. [Frameworks en la industria](#4-frameworks-en-la-industria)
5. [Conceptos fundamentales](#5-conceptos-fundamentales)
6. [El patrón AAA](#6-el-patrón-aaa-arrange-act-assert)
7. [Cómo pensar un test](#7-cómo-pensar-un-test)
8. [Qué testear y qué no testear](#8-qué-testear-y-qué-no-testear)
9. [Mocks, Stubs y Spies](#9-mocks-stubs-y-spies)
10. [Mejores prácticas](#10-mejores-prácticas)
11. [TDD: escribir el test primero](#11-tdd-escribir-el-test-primero)
12. [Ejemplos progresivos](#12-ejemplos-progresivos)
13. [Errores comunes](#13-errores-comunes)

---

## 1. El problema que resuelven los tests

Imaginá que modificás una función en tu código. Funciona bien en tu máquina. Hacés deploy. Algo en producción explota. ¿Por qué? Porque la función que tocaste era usada por otras tres partes del sistema que no recordabas.

Este es el ciclo de miedo que sufre todo equipo sin tests:

```
Cambio código → Rezo → Espero que no rompa nada → Aparece un bug → Debuggeo horas
```

Con tests, el ciclo se convierte en:

```
Cambio código → Corro los tests → Veo exactamente qué rompí en segundos → Lo arreglo
```

Los tests automatizados son una **red de seguridad**. No eliminan los bugs, pero te dicen exactamente dónde están, en el momento en que los creás, no tres semanas después cuando ya no recordás qué cambiaste.

---

## 2. ¿Qué es una prueba unitaria?

Una prueba unitaria es un programa pequeño que verifica que **una unidad de código** (una función, un método, una clase) se comporta exactamente como esperás, de forma **automática** y **repetible**.

La palabra clave es **automática**: la corres una vez, cien veces, o cada vez que hacés un commit, y siempre te da el mismo resultado honesto.

### La analogía del interruptor de luz

Cuando un electricista instala un interruptor, lo prueba: lo enciende, lo apaga. Si funciona, sigue adelante. Si no, lo arregla ahí mismo.

Eso es exactamente un test unitario: verificás que el interruptor (tu función) funciona antes de emparar la pared (integrar con el resto del sistema). Es mucho más barato arreglar el interruptor antes de cerrar la pared que después.

### ¿Qué hace un test unitario?

```
1. Prepara las condiciones (datos de entrada, objetos necesarios)
2. Ejecuta el código que querés probar
3. Verifica que el resultado es el esperado
4. Reporta PASS o FAIL automáticamente
```

Un test que pasa:

```
[PASS] calcularDescuento_con50Productos_retorna10PorcientoDeDescuento
```

Un test que falla:

```
[FAIL] calcularDescuento_con50Productos_retorna10PorcientoDeDescuento
       Esperado: 90.0
       Actual:   100.0
```

Con ese mensaje sabés exactamente qué falló, sin necesidad de debuggear.

---

## 3. La pirámide de testing

No todos los tests son iguales. La industria organiza los tests en una pirámide:

```
          /\
         /  \
        / E2E\          ← Pocos: lentos, frágiles, costosos
       /------\
      /Integrac.\       ← Algunos: prueban que los módulos se comunican bien
     /------------\
    / Unitarios    \    ← Muchos: rápidos, baratos, confiables
   /--------------  \
```

### Tests unitarios (base de la pirámide)
- Prueban una función o clase **en aislamiento**
- No usan base de datos, no llaman APIs externas, no leen archivos
- Se ejecutan en milisegundos
- Son deterministas: siempre dan el mismo resultado

### Tests de integración (medio)
- Prueban que dos o más módulos funcionan juntos
- Pueden usar una base de datos de prueba, un servidor de correo falso, etc.
- Son más lentos pero verifican que los componentes se conectan bien

### Tests end-to-end (cima)
- Simulan un usuario real usando el sistema completo
- Son los más lentos y los más frágiles
- Se rompen por cualquier cambio de UI o de infraestructura

**Regla general:** la mayor parte de tu cobertura debe venir de tests unitarios. Son los más rápidos de escribir, los más rápidos de correr y los más fáciles de mantener.

---

## 4. Frameworks en la industria

Cada lenguaje tiene sus herramientas estándar de testing. Acá están las más usadas:

### Java / Kotlin
| Framework | Rol | Descripción |
|-----------|-----|-------------|
| **JUnit 5** | Runner + Assertions | El estándar de la industria para Java. Define `@Test`, `@BeforeEach`, `assertThat()`, etc. |
| **Mockito** | Mocking | Crea objetos falsos (mocks) que simulan dependencias |
| **AssertJ** | Assertions fluidas | Assertions más expresivas: `assertThat(result).isEqualTo(5)` |
| **DataFaker** | Datos de prueba | Genera datos realistas aleatorios para los tests |
| **WireMock** | Servidor HTTP falso | Simula servicios externos (APIs REST) |

### JavaScript / TypeScript
| Framework | Descripción |
|-----------|-------------|
| **Jest** | El más popular. Runner, assertions y mocking en un solo paquete |
| **Vitest** | Alternativa moderna, compatible con Jest, más rápido |
| **Testing Library** | Para testear componentes de React/Vue/Angular |

### Python
| Framework | Descripción |
|-----------|-------------|
| **pytest** | El estándar actual. Simple, extensible, muy poderoso |
| **unittest** | Viene incluido en Python, más verboso |
| **unittest.mock** | Mocking incluido en la librería estándar |

### C# / .NET
| Framework | Descripción |
|-----------|-------------|
| **xUnit** | El más usado en proyectos modernos de .NET |
| **NUnit** | Muy maduro, ampliamente adoptado |
| **Moq** | Framework de mocking para .NET |

### Go
Go tiene testing incorporado en el lenguaje. El paquete `testing` de la librería estándar es todo lo que necesitás para empezar.

### PHP
**PHPUnit** es el estándar absoluto en el ecosistema PHP.

---

## 5. Conceptos fundamentales

Antes de escribir tu primer test, hay tres términos que vas a ver en todos lados:

### SUT (System Under Test)
Es **el código que estás probando**. Puede ser una función, un método o una clase completa. Todo lo demás en el test existe para apoyar al SUT.

```java
// El SUT es la clase CalculadoraDescuentos
// Todo lo demás (datos de entrada, mocks) existe para probarla
CalculadoraDescuentos sut = new CalculadoraDescuentos();
```

### Dependencias
El SUT generalmente necesita otras cosas para funcionar: una base de datos, un servicio externo, la fecha y hora actual. Esas cosas son sus **dependencias**.

El problema de las dependencias en tests es que:
- Una base de datos puede no estar disponible en el entorno de CI
- Un servicio externo puede estar caído o devolver respuestas distintas
- La fecha y hora cambia constantemente

Por eso, en tests unitarios las dependencias se **reemplazan por objetos falsos**.

### Estado vs Comportamiento
Hay dos cosas que podés verificar en un test:

**Estado:** el valor que retorna el SUT
```java
int resultado = calculadora.sumar(2, 3);
assertThat(resultado).isEqualTo(5);  // Verifico el estado (el resultado)
```

**Comportamiento:** si el SUT llamó a algo que debía llamar
```java
// Verifico que se guardó en la base de datos
verify(repositorio).save(ordenCapturada);
```

---

## 6. El patrón AAA (Arrange, Act, Assert)

Todo test bien escrito tiene tres partes. Se llama el **patrón AAA**:

```
ARRANGE  →  Preparar todo lo necesario para el test
ACT      →  Ejecutar el código que querés probar
ASSERT   →  Verificar que el resultado es el esperado
```

Es el patrón más universal del testing. Lo vas a ver en todos los lenguajes y frameworks.

### Ejemplo en Java

```java
@Test
void calcularTotal_con3ItemsA10Pesos_retorna30() {
    // ARRANGE - Preparo los datos
    Carrito carrito = new Carrito();
    carrito.agregar(new Item("Leche", 10.0));
    carrito.agregar(new Item("Pan", 10.0));
    carrito.agregar(new Item("Manteca", 10.0));

    // ACT - Ejecuto el código a probar
    double total = carrito.calcularTotal();

    // ASSERT - Verifico el resultado
    assertThat(total).isEqualTo(30.0);
}
```

### Ejemplo en Python

```python
def test_calcular_total_con_3_items_a_10_retorna_30():
    # Arrange
    carrito = Carrito()
    carrito.agregar(Item("Leche", 10.0))
    carrito.agregar(Item("Pan", 10.0))
    carrito.agregar(Item("Manteca", 10.0))

    # Act
    total = carrito.calcular_total()

    # Assert
    assert total == 30.0
```

### Ejemplo en JavaScript

```javascript
test('calcularTotal con 3 items a 10 retorna 30', () => {
    // Arrange
    const carrito = new Carrito();
    carrito.agregar(new Item('Leche', 10.0));
    carrito.agregar(new Item('Pan', 10.0));
    carrito.agregar(new Item('Manteca', 10.0));

    // Act
    const total = carrito.calcularTotal();

    // Assert
    expect(total).toBe(30.0);
});
```

Las tres secciones hacen que el test sea fácil de leer. Cualquiera puede entender qué está probando sin necesidad de comentarios adicionales.

---

## 7. Cómo pensar un test

Esta es la parte que más cuesta al principio. Sabés que tenés que hacer un test, pero no sabés **qué** testear exactamente.

### Paso 1: Identificar el SUT y su contrato

Mirá la función que querés testear. ¿Qué promete hacer? ¿Qué recibe? ¿Qué devuelve?

```java
// Contrato: recibe un precio y una cantidad, devuelve el total con descuento
// Si cantidad >= 10, aplica 10% de descuento
// Si cantidad >= 50, aplica 20% de descuento
double calcularPrecioFinal(double precioUnitario, int cantidad) { ... }
```

### Paso 2: Listar los escenarios

Para cada función, preguntate:

- **Happy path**: ¿qué pasa cuando todo funciona normalmente?
- **Caminos alternativos**: ¿qué pasa con cada variante válida de entrada?
- **Casos límite (edge cases)**: ¿qué pasa en los bordes? (0, -1, null, lista vacía)
- **Caminos de error**: ¿qué pasa cuando la entrada es inválida?

Para la función de arriba:

```
Happy path:
  - 5 unidades → sin descuento
  - 10 unidades → 10% de descuento
  - 50 unidades → 20% de descuento

Casos límite:
  - 9 unidades → sin descuento (borde inferior del primer descuento)
  - 10 unidades → 10% (borde superior, ya incluido)
  - 49 unidades → 10% (borde inferior del segundo descuento)

Casos de error:
  - cantidad negativa → ¿lanza excepción?
  - precio negativo → ¿lanza excepción?
```

### Paso 3: Un escenario = un test

Cada ítem de esa lista es un test. No intentes probar todo en un solo test.

```java
@Test
void calcularPrecioFinal_con5Unidades_noAplicaDescuento() { ... }

@Test
void calcularPrecioFinal_con10Unidades_aplica10PorCiento() { ... }

@Test
void calcularPrecioFinal_con50Unidades_aplica20PorCiento() { ... }

@Test
void calcularPrecioFinal_con9Unidades_noAplicaDescuento() { ... }  // borde

@Test
void calcularPrecioFinal_con49Unidades_aplica10PorCiento() { ... } // borde

@Test
void calcularPrecioFinal_conCantidadNegativa_lanzaExcepcion() { ... }
```

### Cómo nombrar los tests

El nombre de un test es su documentación. Seguí este patrón:

```
metodoProbado_condicion_resultadoEsperado
```

Ejemplos:
- `calcularTotal_carritoVacio_retornaCero`
- `login_usuarioNoExiste_retorna401`
- `guardarProducto_sinNombre_lanzaIllegalArgumentException`

Cuando un test falla, el nombre te dice exactamente qué salió mal sin necesidad de leer el código.

---

## 8. Qué testear y qué no testear

### Testear SIEMPRE

**Lógica de negocio**: toda regla del dominio debe tener tests.
```java
// Esto tiene lógica compleja → testear
double aplicarDescuento(Pedido pedido) {
    if (pedido.esClienteVip() && pedido.getTotal() > 1000) {
        return pedido.getTotal() * 0.85;
    }
    return pedido.getTotal();
}
```

**Casos de error y excepciones**: verificar que el código falla de la manera correcta.
```java
// Verificar que lanza la excepción adecuada
@Test
void buscarProducto_idInexistente_lanzaNotFoundException() {
    assertThatThrownBy(() -> servicio.buscar(999L))
        .isInstanceOf(ProductoNotFoundException.class)
        .hasMessage("Producto no encontrado: 999");
}
```

**Algoritmos y cálculos**: cualquier transformación de datos.

**Condiciones de borde**: el 0, el null, la lista vacía, el string vacío.

### NO testear

**Código trivial sin lógica**:
```java
// No tiene sentido testear esto
public String getNombre() {
    return nombre;
}
```

**Código de terceros**: no tests para Spring, Hibernate, la librería de JSON. Ellos ya tienen sus propios tests.

**Configuración de infraestructura**: la definición de un `@Bean` en Spring no tiene lógica que pueda fallar.

**Lo que ya probaste indirectamente**: si `calcularTotal()` llama a `aplicarDescuento()`, y tenés tests de `calcularTotal()` que cubren el descuento, no necesitás también testear `aplicarDescuento()` desde `calcularTotal()` otra vez.

---

## 9. Mocks, Stubs y Spies

Esta es la parte que más confunde al principio. Todos estos términos describen **objetos falsos** que reemplazan dependencias reales en los tests.

### ¿Por qué necesitamos objetos falsos?

Imaginá que tenés un servicio que envía emails:

```java
class ServicioRegistro {
    private EmailSender emailSender;

    void registrarUsuario(String email) {
        // ... guarda en la base de datos ...
        emailSender.enviar(email, "Bienvenido!");
    }
}
```

Para testear `registrarUsuario()`, no querés mandar emails reales. Querés reemplazar `emailSender` por algo que:
1. No mande el email de verdad
2. Te deje verificar si intentó mandarlo

Ahí entran los objetos falsos.

### Stub

Un **stub** es un objeto falso que devuelve respuestas predefinidas. Usás stubs cuando el SUT necesita datos de una dependencia pero vos controlás qué datos recibe.

```java
// En vez de llamar a la base de datos real, el stub siempre devuelve este producto
when(repositorioProductos.buscar(1L))
    .thenReturn(new Producto(1L, "Leche", 150.0));
```

Pregunta que responde un stub: "¿qué datos tiene el repositorio?"
Respuesta que programás: "tiene un producto llamado Leche que cuesta 150."

### Mock

Un **mock** es un objeto falso que te permite verificar si fue llamado y con qué argumentos. Usás mocks para verificar **comportamiento**.

```java
// Verifico que el servicio efectivamente intentó enviar el email
verify(emailSender).enviar("usuario@ejemplo.com", "Bienvenido!");
```

Pregunta que responde un mock: "¿se llamó a este método con estos argumentos?"

### Spy

Un **spy** envuelve un objeto real y te deja observar sus llamadas sin reemplazar su comportamiento. El objeto real hace su trabajo, pero vos podés verificar qué métodos llamó y cuántas veces.

```java
@Spy
private List<String> listaSpy = new ArrayList<>();

// La lista funciona normalmente, pero podés verificar que se llamó a add()
listaSpy.add("elemento");
verify(listaSpy).add("elemento");
```

### Resumen visual

```
Stub  → "Cuando te pregunten X, respondé Y" (controla datos de entrada al SUT)
Mock  → "Verificame que llamaste a Z con estos argumentos" (verifica comportamiento)
Spy   → "Usá el objeto real, pero observate lo que hace"
```

En Mockito (Java), `@Mock` crea un mock puro. `@Spy` crea un spy. Para stubs, configurás un `@Mock` con `when(...).thenReturn(...)`.

---

## 10. Mejores prácticas

### FIRST: las cinco propiedades de un buen test

Los mejores tests cumplen estas cinco propiedades:

**F - Fast (Rápido)**
Un test debe correr en milisegundos. Si tardás 5 segundos, nadie los va a correr antes de hacer commit.

**I - Isolated (Aislado)**
Un test no debe depender de otro test. El orden de ejecución no debe importar. Cada test debe poder correr solo.

**R - Repeatable (Repetible)**
El mismo test debe dar el mismo resultado siempre, en cualquier máquina, sin importar si hay internet, base de datos, o qué hora es.

**S - Self-validating (Auto-validante)**
El test dice PASS o FAIL por sí mismo. No necesitás leer logs, comparar archivos, ni interpretar nada manualmente.

**T - Timely (Oportuno)**
Los tests se escriben cerca del código que prueban, no semanas después. Idealmente antes (TDD).

---

### Un test, una responsabilidad

Cada test debe verificar exactamente **una cosa**. Si un test falla, querés saber exactamente qué falló.

Mal:
```java
@Test
void testCarrito() {
    // Este test verifica demasiadas cosas
    carrito.agregar(producto);
    assertThat(carrito.size()).isEqualTo(1);
    assertThat(carrito.calcularTotal()).isEqualTo(100.0);
    assertThat(carrito.tieneItems()).isTrue();
    carrito.vaciar();
    assertThat(carrito.size()).isEqualTo(0);
}
```

Bien:
```java
@Test
void agregar_unProducto_size1() { ... }

@Test
void calcularTotal_unProductoDe100_retorna100() { ... }

@Test
void tieneItems_despuesDeAgregar_esTrue() { ... }

@Test
void vaciar_carritoConItems_quedaVacio() { ... }
```

---

### No pongas lógica en los tests

Si en tu test tenés un `if`, un `for` o un cálculo complejo, es una señal de que el test es demasiado complicado.

Mal:
```java
@Test
void calcularDescuento() {
    for (int cantidad : new int[]{10, 20, 50}) {
        double descuento = cantidad >= 50 ? 0.20 : 0.10;
        assertThat(calculadora.calcular(100.0, cantidad))
            .isEqualTo(100.0 * (1 - descuento));
    }
}
```

Bien: un test por caso.

---

### Los datos de prueba deben ser legibles

Usá nombres que dejen en claro qué representan los datos, o usá herramientas como DataFaker para datos realistas.

Mal:
```java
Usuario u = new Usuario("aaa", "bbb@ccc.com", 25);
```

Bien:
```java
String nombre = faker.name().fullName();       // "Carlos Rodríguez"
String email = faker.internet().emailAddress(); // "carlos.rodriguez@gmail.com"
int edad = 25;
Usuario usuario = new Usuario(nombre, email, edad);
```

---

### Mantené los tests cerca del código

En la mayoría de proyectos Java, el código está en `src/main/java` y los tests en `src/test/java`, espejando la misma estructura de paquetes. Esto hace fácil encontrar el test de cualquier clase.

---

## 11. TDD: escribir el test primero

**TDD (Test-Driven Development)** invierte el orden: primero escribís el test, después escribís el código.

El ciclo es:

```
RED   → Escribís un test que falla (el código aún no existe)
GREEN → Escribís el mínimo código para que el test pase
REFACTOR → Mejorás el código sin romper el test
```

### ¿Por qué escribir el test primero?

1. **Te obliga a pensar en el contrato antes de la implementación.** Si no podés escribir el test, es porque no entendés qué debe hacer el código.

2. **Garantiza que el test puede fallar.** Un test que nunca viste fallar podría estar mal escrito y pasar siempre.

3. **Produce código más simple.** Escribís exactamente lo que necesitás para pasar el test, sin funcionalidad extra.

### Ejemplo TDD paso a paso

**Quiero implementar `calcularDescuento(precio, cantidad)`.** Regla: a partir de 10 unidades, 10% de descuento.

**Paso 1 - RED: escribo el test (falla porque la función no existe)**
```java
@Test
void calcularDescuento_con10Unidades_retorna10PorCiento() {
    CalculadoraDescuentos calc = new CalculadoraDescuentos();
    double resultado = calc.calcularDescuento(100.0, 10);
    assertThat(resultado).isEqualTo(90.0);
}
```
Resultado: `FAIL - CalculadoraDescuentos no existe`

**Paso 2 - GREEN: escribo el mínimo código para pasar el test**
```java
class CalculadoraDescuentos {
    double calcularDescuento(double precio, int cantidad) {
        if (cantidad >= 10) {
            return precio * 0.90;
        }
        return precio;
    }
}
```
Resultado: `PASS`

**Paso 3 - REFACTOR: mejoro el código**
```java
class CalculadoraDescuentos {
    private static final double DESCUENTO_MAYOR_CANTIDAD = 0.10;
    private static final int UMBRAL_DESCUENTO = 10;

    double calcularDescuento(double precio, int cantidad) {
        if (cantidad >= UMBRAL_DESCUENTO) {
            return precio * (1 - DESCUENTO_MAYOR_CANTIDAD);
        }
        return precio;
    }
}
```
Resultado: `PASS` (el test sigue pasando)

Repetís el ciclo para cada nuevo escenario que querés cubrir.

---

## 12. Ejemplos progresivos

### Ejemplo 1: función pura (sin dependencias)

Una función pura siempre devuelve el mismo resultado para la misma entrada. Es el caso más simple de testear.

**Código:**
```java
class ValidadorEdad {
    boolean esMayorDeEdad(int edad) {
        if (edad < 0) throw new IllegalArgumentException("Edad no puede ser negativa");
        return edad >= 18;
    }
}
```

**Tests:**
```java
class ValidadorEdadTest {

    ValidadorEdad validador = new ValidadorEdad();

    @Test
    void esMayorDeEdad_con18_retornaTrue() {
        assertThat(validador.esMayorDeEdad(18)).isTrue();
    }

    @Test
    void esMayorDeEdad_con17_retornaFalse() {
        assertThat(validador.esMayorDeEdad(17)).isFalse();
    }

    @Test
    void esMayorDeEdad_con0_retornaFalse() {
        assertThat(validador.esMayorDeEdad(0)).isFalse();
    }

    @Test
    void esMayorDeEdad_edadNegativa_lanzaExcepcion() {
        assertThatThrownBy(() -> validador.esMayorDeEdad(-1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Edad no puede ser negativa");
    }
}
```

---

### Ejemplo 2: clase con dependencias (usando Mock)

Acá el SUT tiene una dependencia (el repositorio) que reemplazamos con un mock.

**Código:**
```java
class ServicioProductos {
    private final ProductoRepositorio repositorio;

    ServicioProductos(ProductoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    Producto buscarPorId(Long id) {
        return repositorio.findById(id)
            .orElseThrow(() -> new ProductoNotFoundException("Producto no encontrado: " + id));
    }
}
```

**Tests:**
```java
@ExtendWith(MockitoExtension.class)
class ServicioProductosTest {

    @Mock
    ProductoRepositorio repositorio;      // Objeto falso — nunca toca la base de datos

    @InjectMocks
    ServicioProductos servicio;           // El SUT, recibe el mock automáticamente

    @Test
    void buscarPorId_productoExiste_retornaProducto() {
        // ARRANGE
        Producto esperado = new Producto(1L, "Leche", 150.0);
        when(repositorio.findById(1L)).thenReturn(Optional.of(esperado));

        // ACT
        Producto resultado = servicio.buscarPorId(1L);

        // ASSERT
        assertThat(resultado.getNombre()).isEqualTo("Leche");
        assertThat(resultado.getPrecio()).isEqualTo(150.0);
    }

    @Test
    void buscarPorId_productoNoExiste_lanzaException() {
        // ARRANGE
        when(repositorio.findById(99L)).thenReturn(Optional.empty());

        // ACT & ASSERT
        assertThatThrownBy(() -> servicio.buscarPorId(99L))
            .isInstanceOf(ProductoNotFoundException.class)
            .hasMessage("Producto no encontrado: 99");
    }
}
```

---

### Ejemplo 3: verificar comportamiento (que se llamó a algo)

A veces no hay un valor de retorno para verificar. Lo que importa es **que el SUT hizo algo**: guardó en la base de datos, envió un email, publicó un evento.

**Código:**
```java
class ServicioNotificaciones {
    private final EmailSender emailSender;

    void notificarCompra(String emailCliente, double total) {
        String mensaje = "Tu compra de $" + total + " fue procesada.";
        emailSender.enviar(emailCliente, mensaje);
    }
}
```

**Test:**
```java
@ExtendWith(MockitoExtension.class)
class ServicioNotificacionesTest {

    @Mock
    EmailSender emailSender;

    @InjectMocks
    ServicioNotificaciones servicio;

    @Test
    void notificarCompra_enviaEmailConMensajeCorrecto() {
        // ARRANGE
        String email = "cliente@ejemplo.com";
        double total = 250.0;

        // ACT
        servicio.notificarCompra(email, total);

        // ASSERT - verifico que el email fue enviado con los argumentos correctos
        verify(emailSender).enviar("cliente@ejemplo.com", "Tu compra de $250.0 fue procesada.");
    }

    @Test
    void notificarCompra_solamenteLlamaEnviarUnaVez() {
        // ACT
        servicio.notificarCompra("cliente@ejemplo.com", 100.0);

        // ASSERT - verifico que no se mandaron emails extra
        verify(emailSender, times(1)).enviar(anyString(), anyString());
    }
}
```

---

### Ejemplo 4: capturar argumentos con ArgumentCaptor

A veces el argumento que le pasás a un mock es un objeto que se construye internamente en el SUT. `ArgumentCaptor` te permite capturarlo y verificarlo.

**Código:**
```java
class ServicioPedidos {
    private final PedidoRepositorio repositorio;

    void crearPedido(String cliente, List<String> items) {
        Pedido pedido = new Pedido();
        pedido.setCliente(cliente);
        pedido.setItems(items);
        pedido.setFecha(LocalDate.now());
        pedido.setEstado("PENDIENTE");
        repositorio.save(pedido);
    }
}
```

**Test:**
```java
@ExtendWith(MockitoExtension.class)
class ServicioPedidosTest {

    @Mock
    PedidoRepositorio repositorio;

    @InjectMocks
    ServicioPedidos servicio;

    @Captor
    ArgumentCaptor<Pedido> pedidoCaptor;  // Captura el Pedido que se le pasa a save()

    @Test
    void crearPedido_guardaPedidoConEstadoPendiente() {
        // ARRANGE
        String cliente = "Juan";
        List<String> items = List.of("Leche", "Pan");

        // ACT
        servicio.crearPedido(cliente, items);

        // ASSERT
        verify(repositorio).save(pedidoCaptor.capture()); // captura el pedido pasado a save()
        Pedido pedidoGuardado = pedidoCaptor.getValue();

        assertThat(pedidoGuardado.getCliente()).isEqualTo("Juan");
        assertThat(pedidoGuardado.getEstado()).isEqualTo("PENDIENTE");
        assertThat(pedidoGuardado.getItems()).containsExactly("Leche", "Pan");
    }
}
```

---

## 13. Errores comunes

### Error 1: testear la implementación, no el contrato

Un test debe verificar **qué hace** el código, no **cómo lo hace**.

Mal (testea implementación):
```java
// Esto se rompe si cambias el nombre del método privado
verify(servicio).calcularDescuentoInterno(100.0);
```

Bien (testea contrato):
```java
// Esto verifica el resultado visible desde afuera
assertThat(servicio.calcularPrecioFinal(100.0, 10)).isEqualTo(90.0);
```

---

### Error 2: un test que nunca puede fallar

```java
@Test
void testAlgo() {
    try {
        servicio.hacerAlgo();
    } catch (Exception e) {
        // silencio — el test siempre pasa aunque tire excepción
    }
}
```

Si querés verificar que no lanza excepción, usá:
```java
assertThatCode(() -> servicio.hacerAlgo()).doesNotThrowAnyException();
```

---

### Error 3: datos hardcodeados que ocultan el intent

Mal:
```java
assertThat(resultado).isEqualTo(47.83); // ¿Por qué 47.83?
```

Bien:
```java
double precio = 59.79;
double descuento = 0.20;
double esperado = precio * (1 - descuento); // 47.832

assertThat(resultado).isCloseTo(esperado, within(0.01));
```

---

### Error 4: tests que dependen entre sí

```java
// MAL: este test falla si el anterior no corrió primero
@Test
void test2_verificaQueSeguardoEnTest1() {
    assertThat(baseDeDatos.contar()).isEqualTo(1);
}
```

Cada test debe configurar todo lo que necesita en su propio `@BeforeEach` o en su sección Arrange.

---

### Error 5: mockear todo (incluso el SUT)

Si estás moqueando el objeto que querés testear, algo está mal.

```java
// MAL: estoy moqueando el SUT
@Mock
ServicioProductos servicio; // ← esto es el SUT, no debería ser un mock

@Test
void test() {
    when(servicio.buscar(1L)).thenReturn(producto); // Estoy testeando el mock, no mi código
    assertThat(servicio.buscar(1L)).isEqualTo(producto); // Trivialmente verdadero
}
```

El SUT nunca se moquea. Solo se moquean sus **dependencias**.

---

## Para seguir aprendiendo

- **"The Art of Unit Testing"** — Roy Osherove. El libro más recomendado para entender los fundamentos.
- **"Test Driven Development: By Example"** — Kent Beck. El creador de TDD explica la técnica con ejemplos.
- **Documentación de JUnit 5**: https://junit.org/junit5/docs/current/user-guide/
- **Documentación de Mockito**: https://javadoc.io/doc/org.mockito/mockito-core/latest/
- **Documentación de pytest**: https://docs.pytest.org
- **Documentación de Jest**: https://jestjs.io/docs/getting-started

---

*Esta guía usa Java como lenguaje principal en los ejemplos porque es el lenguaje del proyecto Supermarket, pero los conceptos (AAA, FIRST, mocks, TDD) aplican exactamente igual en Python, JavaScript, C# y cualquier otro lenguaje.*

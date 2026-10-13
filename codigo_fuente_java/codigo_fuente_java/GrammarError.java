// excepcion que se lanza cuando el texto de una produccion no tiene
// una estructura valida (por ejemplo, si al usuario se le olvido poner
// la flecha "->"). Los errores de contenido (simbolos no declarados,
// simbolo inicial invalido, etc.) no se manejan con excepciones, esos
// se devuelven como una lista de mensajes desde Grammar.validar()
public class GrammarError extends Exception {
    public GrammarError(String mensaje) {
        super(mensaje);
    }
}

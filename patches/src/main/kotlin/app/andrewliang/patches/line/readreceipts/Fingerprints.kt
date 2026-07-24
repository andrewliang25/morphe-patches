package app.andrewliang.patches.line.readreceipts

import app.morphe.patcher.Fingerprint

/**
 * The generic Thrift client dispatch `org.apache.thrift.o.b(String methodName, e args)` —
 * the universal SEND chokepoint every LINE Thrift RPC funnels through (both the TalkService
 * client and the Square/OpenChat client extend `org.apache.thrift.o`).
 *
 * The `org/apache/thrift/` package survives obfuscation; the class (`o`) and method (`b`)
 * names are obfuscated, so we anchor on the distinctive signature: a `void` method taking
 * `(String, org.apache.thrift.e)`. Its sibling `a` (recv) has the same parameter shape, so
 * this could in principle match either — but only `b` is the send half; the read-receipt
 * rewrite is a no-op on the recv half anyway, so matching order is not safety-critical here.
 */
internal object ThriftSendFingerprint : Fingerprint(
    definingClass = "Lorg/apache/thrift/o;",
    name = "b",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Lorg/apache/thrift/e;"),
)

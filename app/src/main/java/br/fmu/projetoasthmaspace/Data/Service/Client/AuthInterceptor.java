package br.fmu.projetoasthmaspace.Data.Service.Client;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import br.fmu.projetoasthmaspace.Core.Session.UserSessionManager;
import br.fmu.projetoasthmaspace.Presentation.ActivityView.LoginActivity;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {

    private static final String TAG = "AuthInterceptor";

    /** Extra lido pela LoginActivity para exibir a mensagem de sessão expirada. */
    public static final String EXTRA_SESSAO_EXPIRADA = "SESSAO_EXPIRADA";

    /**
     * Debounce: quando o token expira, várias requisições em voo recebem 401
     * quase ao mesmo tempo (os fragments carregam em paralelo). Sem esta trava,
     * a LoginActivity seria aberta múltiplas vezes.
     */
    private static final AtomicBoolean tratandoSessaoExpirada = new AtomicBoolean(false);

    private final Context context;

    public AuthInterceptor(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Chamar após login bem-sucedido (LoginActivity) para rearmar o tratamento. */
    public static void resetSessaoExpirada() {
        tratandoSessaoExpirada.set(false);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {

        Request original = chain.request();

        UserSessionManager session = new UserSessionManager(context);
        String token = session.getToken();

        // Sem token = rota pública (login, cadastro, recuperação de senha).
        // Um 401 aqui significa credencial inválida, não sessão expirada —
        // deixa a resposta seguir para a tela tratar.
        if (token == null || token.isEmpty()) {
            return chain.proceed(original);
        }

        Request requestComToken = original.newBuilder()
                .addHeader("Authorization", "Bearer " + token)
                .build();

        Response response = chain.proceed(requestComToken);

        // Enviamos um token e o servidor o rejeitou: sessão expirada/ inválida.
        if (response.code() == 401 || response.code() == 403) {
            tratarSessaoExpirada(session);
        }

        return response;
    }

    private void tratarSessaoExpirada(UserSessionManager session) {
        // compareAndSet garante que só a PRIMEIRA requisição com 401 executa
        // o redirecionamento; as demais em voo são ignoradas.
        if (!tratandoSessaoExpirada.compareAndSet(false, true)) return;

        Log.w(TAG, "Token rejeitado pelo servidor — encerrando sessão.");
        session.clear(context);

        // Interceptor roda em thread de background; navegação vai para a main.
        new Handler(Looper.getMainLooper()).post(() -> {
            Intent intent = new Intent(context, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.putExtra(EXTRA_SESSAO_EXPIRADA, true);
            context.startActivity(intent);
        });
    }
}
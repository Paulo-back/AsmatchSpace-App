package br.fmu.projetoasthmaspace.Core.Session;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.File;
import java.util.Map;

import br.fmu.projetoasthmaspace.Core.Domain.Log.SharedPreferencesKeys;

public class UserSessionManager {

    private static final String TAG = "UserSessionManager";

    private static final String ENCRYPTED_PREFS_FILE = "user_prefs_secure";

    private static final String KEY_NOME = "USER_NAME";
    private static final String KEY_FOTO = "USER_FOTO_PATH";

    /**
     * Cache estático da instância de SharedPreferences.
     * Abrir EncryptedSharedPreferences envolve o Android Keystore e é custoso;
     * como o AuthInterceptor cria um UserSessionManager a cada requisição HTTP,
     * sem este cache haveria latência extra em toda chamada de rede.
     */
    private static volatile SharedPreferences cachedPrefs;

    private final SharedPreferences prefs;

    public UserSessionManager(Context context) {
        prefs = getSecurePrefs(context.getApplicationContext());
    }



    private static SharedPreferences getSecurePrefs(Context context) {
        if (cachedPrefs != null) return cachedPrefs;

        synchronized (UserSessionManager.class) {
            if (cachedPrefs != null) return cachedPrefs;

            try {
                cachedPrefs = criarEncryptedPrefs(context);
            } catch (Exception e) {
                // Keystore corrompido ou arquivo criptografado inconsistente
                // (pode acontecer após restore de backup ou clear parcial de dados).
                // Recupera apagando o arquivo criptografado e recriando do zero —
                // o usuário precisará logar novamente, mas o app não crasha.
                Log.e(TAG, "Falha ao abrir prefs criptografado, recriando: " + e.getMessage());
                context.deleteSharedPreferences(ENCRYPTED_PREFS_FILE);
                try {
                    cachedPrefs = criarEncryptedPrefs(context);
                } catch (Exception fatal) {
                    // Não deixa o app crashar.
                    Log.e(TAG, "EncryptedSharedPreferences indisponível, usando fallback", fatal);
                    cachedPrefs = context.getSharedPreferences(
                            SharedPreferencesKeys.PREFS_FILE_NAME, Context.MODE_PRIVATE);
                    return cachedPrefs;
                }
            }

            migrarPrefsAntigoSePreciso(context, cachedPrefs);
            return cachedPrefs;
        }
    }

    private static SharedPreferences criarEncryptedPrefs(Context context) throws Exception {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();

        return EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }

    /**
     * Migração única: copia os dados do arquivo antigo (texto puro) para o
     * criptografado e limpa o antigo. Executa apenas se o arquivo antigo
     * tiver conteúdo — nas execuções seguintes é no-op.
     */
    private static void migrarPrefsAntigoSePreciso(Context context, SharedPreferences secure) {
        SharedPreferences legado = context.getSharedPreferences(
                SharedPreferencesKeys.PREFS_FILE_NAME, Context.MODE_PRIVATE);

        Map<String, ?> dados = legado.getAll();
        if (dados.isEmpty()) return;

        Log.d(TAG, "Migrando " + dados.size() + " entradas para prefs criptografado.");

        SharedPreferences.Editor editor = secure.edit();
        for (Map.Entry<String, ?> entry : dados.entrySet()) {
            Object valor = entry.getValue();
            if      (valor instanceof String)  editor.putString(entry.getKey(), (String) valor);
            else if (valor instanceof Long)    editor.putLong(entry.getKey(), (Long) valor);
            else if (valor instanceof Integer) editor.putInt(entry.getKey(), (Integer) valor);
            else if (valor instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) valor);
            else if (valor instanceof Float)   editor.putFloat(entry.getKey(), (Float) valor);
        }
        editor.apply();

        // Remove os dados em texto puro do disco.
        legado.edit().clear().apply();
        Log.d(TAG, "Migração concluída, arquivo antigo limpo.");
    }


    public void saveToken(String token) {
        prefs.edit()
                .putString(SharedPreferencesKeys.TOKEN_KEY, token)
                .apply();
    }

    public String getToken() {
        return prefs.getString(SharedPreferencesKeys.TOKEN_KEY, null);
    }

    public void saveClienteId(Long id) {
        prefs.edit().putLong("CLIENTE_ID", id).apply();
    }

    public Long getClienteId() {
        return prefs.getLong("CLIENTE_ID", -1L);
    }


    public void saveNome(String nome) {
        prefs.edit().putString(KEY_NOME, nome).apply();
    }

    public String getNome() {
        return prefs.getString(KEY_NOME, null);
    }


    public void saveFotoPath(String path) {
        prefs.edit().putString(KEY_FOTO, path).apply();
    }

    public String getFotoPath() {
        return prefs.getString(KEY_FOTO, null);
    }


    public void clear() {
        prefs.edit().clear().apply();
    }

    public void logout(Context context) {

        String fotoPath = getFotoPath();
        if (fotoPath != null) {
            File arquivoFoto = new File(fotoPath);
            if (arquivoFoto.exists()) {
                arquivoFoto.delete();
            }
        }

        clear();
    }
}
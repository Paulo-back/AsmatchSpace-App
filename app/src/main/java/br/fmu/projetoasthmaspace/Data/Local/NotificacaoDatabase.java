package br.fmu.projetoasthmaspace.Data.Local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import br.fmu.projetoasthmaspace.Data.worker.NotificacaoDao;

@Database(entities = {NotificacaoEntity.class}, version = 2, exportSchema = false)
public abstract class NotificacaoDatabase extends RoomDatabase {

    public abstract NotificacaoDao dao();

    private static volatile NotificacaoDatabase INSTANCE;

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE notificacoes ADD COLUMN templateId INTEGER NOT NULL DEFAULT -1");
        }
    };

    public static NotificacaoDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (NotificacaoDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            NotificacaoDatabase.class,
                            "notificacoes_db"
                    ).addMigrations(MIGRATION_1_2).build();
                }
            }
        }
        return INSTANCE;
    }
}

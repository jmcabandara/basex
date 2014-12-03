package org.basex.core.cmd;

import static org.basex.core.Text.*;
import static org.basex.data.DataText.*;

import java.io.*;

import org.basex.core.*;
import org.basex.data.*;
import org.basex.io.in.DataInput;
import org.basex.util.*;

/**
 * Evaluates the 'open' command and opens a database.
 *
 * @author BaseX Team 2005-14, BSD License
 * @author Christian Gruen
 */
public final class Open extends Command {
  /**
   * Default constructor.
   * @param name name of database
   */
  public Open(final String name) {
    this(name, null);
  }

  /**
   * Default constructor.
   * @param name name of database
   * @param path database path
   */
  public Open(final String name, final String path) {
    super(Perm.NONE, name, path);
  }

  @Override
  protected boolean run() {
    final String db = args[0];
    if(!Databases.validName(db)) return error(NAME_INVALID_X, db);

    // check if database is already opened
    Data data = context.data();
    if(data == null || !data.meta.name.equals(db)) {
      new Close().run(context);
      try {
        data = open(db, context);
        context.openDB(data);

        final String path = args[1];
        if(path != null && !path.isEmpty()) {
          context.current(new DBNodes(data, data.resources.docs(path).toArray()));
        }
        if(data.meta.oldindex()) info(H_INDEX_FORMAT);
        if(data.meta.corrupt)  info(DB_CORRUPT);
      } catch(final IOException ex) {
        return error(Util.message(ex));
      }
    }
    return info(DB_OPENED_X, db, perf);
  }

  @Override
  public void databases(final LockResult lr) {
    lr.read.add(DBLocking.CTX).add(args[0]);
  }

  @Override
  public boolean newData(final Context ctx) {
    return new Close().run(ctx);
  }

  /**
   * Opens the specified database.
   * @param name name of database
   * @param ctx database context
   * @return data reference
   * @throws IOException I/O exception
   */
  public static Data open(final String name, final Context ctx) throws IOException {
    // [CG] USERS: check permissions before opening database

    synchronized(ctx.dbs) {
      Data data = ctx.dbs.pin(name);
      if(data != null) {
        // check permissions in opened database
        if(!ctx.perm(Perm.READ, data.meta)) {
          ctx.dbs.unpin(data);
          throw new BaseXException(PERM_REQUIRED_X, Perm.READ);
        }
      } else {
        // check if the addressed database exists
        if(!ctx.soptions.dbexists(name)) throw new BaseXException(dbnf(name));

        // do not open a database that is currently updated
        final MetaData meta = new MetaData(name, ctx);
        if(meta.updateFile().exists()) throw new BaseXException(Text.DB_UPDATED_X, meta.name);

        // open meta data and database
        try(final DataInput in = new DataInput(meta.dbfile(DATAINF))) {
          meta.read(in);
          // open database if user has permissions
          if(!ctx.perm(Perm.READ, meta)) throw new BaseXException(PERM_REQUIRED_X, Perm.READ);
          data = new DiskData(meta, in);
        }
        ctx.dbs.add(data);
      }
      return data;
    }
  }

  /**
   * Returns an error message for an unknown database.
   * @param name name of database
   * @return error message
   */
  public static String dbnf(final String name) {
    return Util.info(DB_NOT_FOUND_X, name);
  }
}

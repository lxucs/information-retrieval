package emory.ir;

import emory.ir.index.IndexFiles;
import emory.ir.search.SearchFiles;

public class Runner {

    public static void main(String[] args) throws Exception {
        if(args.length > 0 && args[0].equalsIgnoreCase("indexing"))
            IndexFiles.run(args);
        else
            SearchFiles.run(args);
    }

}

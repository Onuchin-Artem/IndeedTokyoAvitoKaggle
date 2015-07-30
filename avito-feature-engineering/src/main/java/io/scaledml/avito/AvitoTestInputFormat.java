package io.scaledml.avito;

import java.io.IOException;

/**
 * Created by artem on 7/9/15.
 */
public class AvitoTestInputFormat extends AvitoInputFormat {

    public AvitoTestInputFormat() throws IOException {
        super();
        hasIds = true;
    }
}

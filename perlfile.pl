require "cgi-lib.pl";
if (&ReadParse(*input)) {
  print &PrintHeader, &PrintVariables(%input);
} else {
  print &PrintHeader,'<form><input type="submit">Data: <input name="myfield">';
}

from pathlib import Path
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from run_g2_direct_gguf_acceptance import exact_target

class VisibleSafTargets(unittest.TestCase):
    def test_refuses_wrong_package_before_typing_checksum(self):
        xml='<hierarchy><node class="android.widget.EditText" package="other.app" bounds="[0,0][80,40]"/></hierarchy>'
        with self.assertRaises(RuntimeError):exact_target(xml,'',package='org.localpilot.android.dev.unified',editable=True)

    def test_refuses_ambiguous_inputs_and_disabled_or_empty_file(self):
        node='<node class="android.widget.EditText" package="own" bounds="[0,0][80,40]"/>'
        with self.assertRaises(RuntimeError):exact_target('<hierarchy>'+node+node+'</hierarchy>','',package='own',editable=True)
        for attrs in ('enabled="false" bounds="[0,0][80,40]"','bounds="[0,0][0,0]"'):
            with self.subTest(attrs=attrs),self.assertRaises(RuntimeError):
                exact_target('<hierarchy><node text="fixture.gguf" '+attrs+'/></hierarchy>','fixture.gguf')

    def test_exact_filename_never_matches_similar_document(self):
        xml='<hierarchy><node text="fixture.gguf.backup" bounds="[0,0][80,40]"/></hierarchy>'
        with self.assertRaises(RuntimeError):exact_target(xml,'fixture.gguf')

    def test_unique_visible_input(self):
        xml='<hierarchy><node class="android.widget.EditText" package="own" bounds="[10,20][110,60]"/></hierarchy>'
        self.assertEqual(exact_target(xml,'',package='own',editable=True),(60,40))

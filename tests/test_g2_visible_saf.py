from pathlib import Path
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from run_g2_direct_gguf_acceptance import exact_target
from run_real_nes_vision_acceptance import confirms_control,parse_game_state

class VisibleSafTargets(unittest.TestCase):
    def test_background_or_wrong_direction_is_not_game_control(self):
        before=parse_game_state('1,100,180,3,3,1,0,0,0,0')
        after=parse_game_state('1,116,180,3,3,1,0,0,1,1')
        self.assertTrue(confirms_control('RIGHT',before,after))
        self.assertTrue(confirms_control('A',before,after))
        self.assertFalse(confirms_control('A',before,before))
        self.assertFalse(confirms_control('LEFT',before,after))
        with self.assertRaises(ValueError):parse_game_state('1,100,180,3,3,1,999999999,0,0,0')
    def test_game_geometry_requires_complete_active_coordinates(self):
        state=parse_game_state('1,100,180,3,3,1,0,0,1,1,90,40,104,160')
        self.assertEqual(state['nearestEnemy'],dict(x=90,y=40))
        self.assertEqual(state['nearestHostileBullet'],dict(x=104,y=160))
        absent=parse_game_state('1,100,180,3,3,1,0,0,1,1,-1,-1,-1,-1')
        self.assertIsNone(absent['nearestEnemy'])
        for geometry in ('-1,40,104,160','90,40,104','90,40,104,999'):
            with self.assertRaises(ValueError):parse_game_state('1,100,180,3,3,1,0,0,1,1,'+geometry)

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

    def test_delete_confirmation_matches_android_button_caps_only(self):
        xml='<hierarchy><node text="Удалить выбранный GGUF?" class="android.widget.TextView" package="own" bounds="[0,0][80,40]"/><node text="УДАЛИТЬ" class="android.widget.Button" package="own" bounds="[10,40][110,80]"/></hierarchy>'
        self.assertEqual(exact_target(xml,'Удалить',package='own',button=True),(60,60))
        with self.assertRaises(RuntimeError):exact_target(xml,'Удалить',package='other',button=True)
        with self.assertRaises(RuntimeError):exact_target(xml,'Удалить выбранный',package='own',button=True)

    def test_unique_visible_input(self):
        xml='<hierarchy><node class="android.widget.EditText" package="own" bounds="[10,20][110,60]"/></hierarchy>'
        self.assertEqual(exact_target(xml,'',package='own',editable=True),(60,40))
